package com.nfv.validator.service;

import com.nfv.validator.model.api.*;
import com.nfv.validator.model.batch.BatchValidationRequest;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        return createJobWithId(jobId, request);
    }

    /**
     * Create a new validation job with specific ID (for batch sub-jobs)
     */
    public ValidationJobResponse createJobWithId(String jobId, ValidationJobRequest request) {
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
     * Create a new batch validation job
     */
    public ValidationJobResponse createBatchJob(BatchValidationRequest request) {
        String jobId = UUID.randomUUID().toString();
        
        ValidationJobResponse response = ValidationJobResponse.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .submittedAt(Instant.now())
                .message("Batch validation job queued with " + request.getRequests().size() + " sub-requests")
                .individualJobIds(new ArrayList<>()) // Initialize empty list
                .build();
        
        jobs.put(jobId, response);
        
        log.info("Created new batch validation job: {}", jobId);
        return response;
    }
    
    /**
     * Add individual job ID to batch job
     */
    public void addIndividualJobToBatch(String batchJobId, String individualJobId) {
        ValidationJobResponse batchJob = jobs.get(batchJobId);
        if (batchJob != null) {
            if (batchJob.getIndividualJobIds() == null) {
                batchJob.setIndividualJobIds(new ArrayList<>());
            }
            batchJob.getIndividualJobIds().add(individualJobId);
            log.debug("Added individual job {} to batch {}", individualJobId, batchJobId);
        }
    }
    
    /**
     * Get job status by ID
     */
    public ValidationJobResponse getJob(String jobId) {
        return jobs.get(jobId);
    }
    
    /**
     * Get list of individual job IDs for a batch job
     */
    public List<String> getIndividualJobsForBatch(String batchJobId) {
        List<String> individualJobs = new ArrayList<>();
        
        // Look for jobs with IDs starting with {batchJobId}-
        String prefix = batchJobId + "-";
        for (String jobId : jobs.keySet()) {
            if (jobId.startsWith(prefix)) {
                individualJobs.add(jobId);
            }
        }
        
        // Sort by suffix number (1, 2, 3, ...)
        individualJobs.sort((a, b) -> {
            try {
                int suffixA = Integer.parseInt(a.substring(prefix.length()));
                int suffixB = Integer.parseInt(b.substring(prefix.length()));
                return Integer.compare(suffixA, suffixB);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        
        log.debug("Found {} individual jobs for batch {}", individualJobs.size(), batchJobId);
        return individualJobs;
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
            job.setDownloadUrl("/kvalidator/api/validate/" + jobId + "/download");
            job.setJsonUrl("/kvalidator/api/validate/" + jobId + "/json");
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
     * Checks for CNF checklist report first, then falls back to standard validation report
     */
    public File getExcelReportFile(String jobId) {
        Path dir = getJobResultsDirectory(jobId);
        
        // Check for CNF checklist report first
        File cnfXlsxFile = dir.resolve("cnf-checklist-validation.xlsx").toFile();
        if (cnfXlsxFile.exists()) {
            return cnfXlsxFile;
        }
        
        // Fall back to standard validation report
        File xlsxFile = dir.resolve("validation-report.xlsx").toFile();
        if (xlsxFile.exists()) {
            return xlsxFile;
        }
        
        return null;
    }
    
    /**
     * Get JSON results file for a job
     * Prioritizes CNF results (with flexible matching) over standard validation results
     */
    public File getJsonResultsFile(String jobId) {
        Path dir = getJobResultsDirectory(jobId);
        
        // Prefer CNF results if available (includes flexible matching)
        File cnfFile = dir.resolve("cnf-results.json").toFile();
        if (cnfFile.exists()) {
            return cnfFile;
        }
        
        // Fall back to standard validation results
        File jsonFile = dir.resolve("validation-results.json").toFile();
        if (jsonFile.exists()) {
            return jsonFile;
        }
        
        return null;
    }
    
    /**
     * Export batch results as ZIP file
     * Only includes .xlsx files, named by job description
     */
    public File exportBatchResultsAsZip(String batchJobId) throws Exception {
        File baseDir = new File(RESULTS_BASE_DIR);
        
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            throw new IllegalStateException("Results directory not found");
        }
        
        // Create temporary ZIP file
        File tempZip = File.createTempFile("batch-results-" + batchJobId, ".zip");
        tempZip.deleteOnExit();
        
        int fileCount = 0;
        
        try (FileOutputStream fos = new FileOutputStream(tempZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            // Find all directories that belong to individual jobs in this batch
            // Pattern: /tmp/.kvalidator/results/{batchJobId}-1/, {batchJobId}-2/, etc.
            File[] allResultDirs = baseDir.listFiles();
            if (allResultDirs != null) {
                for (File dir : allResultDirs) {
                    if (dir.isDirectory()) {
                        String dirName = dir.getName();
                        
                        // Check if this directory belongs to any individual job in this batch
                        // Format: {batchJobId}-{index}
                        if (dirName.startsWith(batchJobId + "-")) {
                            String individualJobId = dirName;
                            
                            // Get the job to retrieve validation name
                            ValidationJobResponse individualJob = getJob(individualJobId);
                            String jobName;
                            if (individualJob != null && individualJob.getValidationName() != null && !individualJob.getValidationName().trim().isEmpty()) {
                                jobName = individualJob.getValidationName().trim();
                            } else {
                                // Fallback to validation-index naming
                                String suffix = individualJobId.substring((batchJobId + "-").length());
                                jobName = "validation-" + suffix;
                            }
                            
                            // Sanitize filename (remove special chars, keep spaces as underscores)
                            jobName = jobName.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", "_");
                            
                            // Only add .xlsx files
                            File[] files = dir.listFiles((d, name) -> name.endsWith(".xlsx"));
                            if (files != null) {
                                for (File file : files) {
                                    String zipEntryName = jobName + ".xlsx";
                                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                                    zos.putNextEntry(zipEntry);
                                    
                                    try (FileInputStream fis = new FileInputStream(file)) {
                                        byte[] buffer = new byte[8192];
                                        int length;
                                        while ((length = fis.read(buffer)) > 0) {
                                            zos.write(buffer, 0, length);
                                        }
                                    }
                                    
                                    zos.closeEntry();
                                    fileCount++;
                                }
                            }
                        }
                    }
                }
            }
            
            // Add batch summary file if it exists
            File batchDir = new File(RESULTS_BASE_DIR, batchJobId);
            if (batchDir.exists() && batchDir.isDirectory()) {
                File[] batchFiles = batchDir.listFiles((d, name) -> name.endsWith(".xlsx"));
                if (batchFiles != null) {
                    for (File file : batchFiles) {
                        String zipEntryName = "batch-summary.xlsx";
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        zos.putNextEntry(zipEntry);
                        
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                        }
                        
                        zos.closeEntry();
                        fileCount++;
                    }
                }
            }
            
            zos.finish();
        }
        
        if (fileCount == 0) {
            throw new IllegalStateException("No batch results found for job " + batchJobId);
        }
        
        log.info("Created batch ZIP with {} files for job {}", fileCount, batchJobId);
        return tempZip;
    }
}
