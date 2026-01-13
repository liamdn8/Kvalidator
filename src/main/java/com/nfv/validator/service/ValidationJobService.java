package com.nfv.validator.service;

import com.nfv.validator.model.api.*;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing validation job lifecycle and state
 */
@Slf4j
@ApplicationScoped
public class ValidationJobService {
    
    private static final String RESULTS_BASE_DIR = "/tmp/.kvalidator/results";
    
    // In-memory job storage (for production, use Redis or database)
    private final Map<String, ValidationJobResponse> jobs = new ConcurrentHashMap<>();
    
    /**
     * Create a new validation job
     */
    public ValidationJobResponse createJob(ValidationJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        
        ValidationJobResponse response = ValidationJobResponse.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .submittedAt(Instant.now())
                .message("Validation job queued")
                .build();
        
        jobs.put(jobId, response);
        
        log.info("Created new validation job: {}", jobId);
        return response;
    }
    
    /**
     * Get job status by ID
     */
    public ValidationJobResponse getJob(String jobId) {
        return jobs.get(jobId);
    }
    
    /**
     * Update job to processing state
     */
    public void startJob(String jobId) {
        ValidationJobResponse job = jobs.get(jobId);
        if (job != null) {
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(JobProgress.builder()
                    .currentStep("Initializing validation")
                    .percentage(0)
                    .namespacesProcessed(0)
                    .objectsCompared(0)
                    .build());
            
            log.info("Started validation job: {}", jobId);
        }
    }
    
    /**
     * Update job progress
     */
    public void updateProgress(String jobId, JobProgress progress) {
        ValidationJobResponse job = jobs.get(jobId);
        if (job != null && job.getStatus() == JobStatus.PROCESSING) {
            job.setProgress(progress);
            log.debug("Updated job {} progress: {}%", jobId, progress.getPercentage());
        }
    }
    
    /**
     * Mark job as completed successfully
     */
    public void completeJob(String jobId, String reportPath, int objectsCompared, int differencesFound) {
        ValidationJobResponse job = jobs.get(jobId);
        if (job != null) {
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setReportPath(reportPath);
            job.setObjectsCompared(objectsCompared);
            job.setDifferencesFound(differencesFound);
            job.setMessage("Validation completed successfully");
            job.setDownloadUrl("/api/validate/" + jobId + "/download");
            job.setJsonUrl("/api/validate/" + jobId + "/json");
            job.setProgress(null); // Clear progress info
            
            log.info("Completed validation job: {} (objects: {}, differences: {})", 
                    jobId, objectsCompared, differencesFound);
        }
    }
    
    /**
     * Mark job as failed
     */
    public void failJob(String jobId, String errorMessage) {
        ValidationJobResponse job = jobs.get(jobId);
        if (job != null) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setMessage(errorMessage);
            job.setProgress(null);
            
            log.error("Failed validation job: {} - {}", jobId, errorMessage);
        }
    }
    
    /**
     * Get the results directory path for a job
     */
    public Path getJobResultsDirectory(String jobId) {
        return Paths.get(RESULTS_BASE_DIR, jobId);
    }
    
    /**
     * Ensure results directory exists for a job
     */
    public Path createJobResultsDirectory(String jobId) throws Exception {
        Path dir = getJobResultsDirectory(jobId);
        Files.createDirectories(dir);
        return dir;
    }
    
    /**
     * Get Excel report file for a job
     */
    public File getExcelReportFile(String jobId) {
        Path dir = getJobResultsDirectory(jobId);
        File xlsxFile = dir.resolve("validation-report.xlsx").toFile();
        
        if (xlsxFile.exists()) {
            return xlsxFile;
        }
        
        return null;
    }
    
    /**
     * Get JSON results file for a job
     */
    public File getJsonResultsFile(String jobId) {
        Path dir = getJobResultsDirectory(jobId);
        File jsonFile = dir.resolve("validation-results.json").toFile();
        
        if (jsonFile.exists()) {
            return jsonFile;
        }
        
        return null;
    }
}
