package com.nfv.validator.service;

import com.nfv.validator.model.cnf.BatchYamlToCNFRequest;
import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.model.cnf.ConversionJobResponse;
import com.nfv.validator.model.cnf.NamespaceTarget;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

/**
 * Async executor for YAML to CNF conversion jobs
 */
@Slf4j
@ApplicationScoped
public class AsyncConversionExecutor {

    @Inject
    YamlToCNFChecklistConverter yamlConverter;

    @Inject
    CNFChecklistFileParser fileParser;

    // Job storage (in-memory, in production should use database)
    private final Map<String, ConversionJobResponse> jobs = new ConcurrentHashMap<>();
    
    // Thread pool for async execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    // Results directory
    private static final String RESULTS_DIR = "/tmp/.kvalidator/conversion-results";

    /**
     * Submit a batch conversion job
     * Creates one job per target (cluster-namespace pair)
     */
    public List<ConversionJobResponse> submitConversionJob(BatchYamlToCNFRequest request) {
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            log.error("Invalid conversion request: {}", e.getMessage());
            throw e;
        }

        List<ConversionJobResponse> createdJobs = new ArrayList<>();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        // Create one job for each target (cluster-namespace pair)
        for (NamespaceTarget target : request.getTargets()) {
            // Generate job ID with cluster and namespace
            String jobId = String.format("conversion-%s-%s-%s", 
                target.getCluster().replaceAll("[^a-zA-Z0-9-]", "_"),
                target.getNamespace().replaceAll("[^a-zA-Z0-9-]", "_"),
                timestamp);

            // Create job response
            ConversionJobResponse job = ConversionJobResponse.builder()
                    .jobId(jobId)
                    .status("PENDING")
                    .targetNamespace(target.getNamespace())
                    .fileCount(request.getYamlFiles().size())
                    .namespaceCount(null)
                    .namespaces(null)
                    .flattenMode(request.getFlattenMode())
                    .description(request.getDescription())
                    .progress(0)
                    .submittedAt(LocalDateTime.now())
                    .build();

            jobs.put(jobId, job);
            createdJobs.add(job);

            // Execute conversion asynchronously
            executorService.submit(() -> executeConversion(jobId, request, target));

            log.info("Submitted conversion job for cluster {} namespace {}: {}", 
                target.getCluster(), target.getNamespace(), jobId);
        }

        return createdJobs;
    }

    /**
     * Get job status
     */
    public ConversionJobResponse getJobStatus(String jobId) {
        ConversionJobResponse job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    /**
     * Get all jobs
     */
    public List<ConversionJobResponse> getAllJobs() {
        return jobs.values().stream()
                .sorted((j1, j2) -> j2.getSubmittedAt().compareTo(j1.getSubmittedAt()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Execute conversion job for a specific target (cluster-namespace pair)
     */
    private void executeConversion(String jobId, BatchYamlToCNFRequest request, NamespaceTarget target) {
        ConversionJobResponse job = jobs.get(jobId);
        
        try {
            log.info("Starting conversion job for cluster {} namespace {}: {}", 
                target.getCluster(), target.getNamespace(), jobId);
            
            // Update status
            job.setStatus("PROCESSING");
            job.setProgress(10);

            // Convert YAML to checklist items for this namespace
            log.info("Converting {} YAML files for cluster {} namespace {} with flatten mode: {}", 
                    request.getYamlFiles().size(), target.getCluster(), target.getNamespace(), 
                    request.getFlattenMode());
            
            // Filter: only include resources from target namespace
            List<String> namespaceFilter = new ArrayList<>();
            namespaceFilter.add(target.getNamespace());
            
            List<CNFChecklistItem> items = yamlConverter.convertMultipleFilesToCNFChecklist(
                    target.getCluster(), // Use cluster as vimName
                    request.getYamlFiles(),
                    namespaceFilter, // Only this namespace
                    request.getImportantFields()
            );

            job.setProgress(60);

            if (items.isEmpty()) {
                log.warn("No checklist items generated for cluster {} namespace: {}", 
                    target.getCluster(), target.getNamespace());
                job.setStatus("COMPLETED");
                job.setProgress(100);
                job.setTotalItems(0);
                job.setCompletedAt(LocalDateTime.now());
                return;
            }

            log.info("Generated {} checklist items for cluster {} namespace {}", 
                items.size(), target.getCluster(), target.getNamespace());
            job.setTotalItems(items.size());
            job.setProgress(70);

            // Generate Excel file
            log.info("Generating Excel file for cluster {} namespace {}", 
                target.getCluster(), target.getNamespace());
            byte[] excelContent;
            try {
                excelContent = fileParser.generateExcelFromItems(items);
                log.info("Excel content generated: {} bytes", excelContent.length);
            } catch (Exception e) {
                log.error("Failed to generate Excel content for cluster {} namespace {}", 
                    target.getCluster(), target.getNamespace(), e);
                throw new IOException("Excel generation failed: " + e.getMessage(), e);
            }
            job.setProgress(90);

            // Save Excel file
            File resultsDir = new File(RESULTS_DIR);
            if (!resultsDir.exists()) {
                boolean created = resultsDir.mkdirs();
                log.info("Results directory created: {} (success: {})", resultsDir.getAbsolutePath(), created);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = String.format("cnf-checklist-%s-%s-%s.xlsx", 
                    target.getCluster().replaceAll("[^a-zA-Z0-9-]", "_"),
                    target.getNamespace().replaceAll("[^a-zA-Z0-9-]", "_"), 
                    timestamp);
            
            File excelFile = new File(resultsDir, filename);
            log.info("Saving Excel file to: {}", excelFile.getAbsolutePath());
            
            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                fos.write(excelContent);
                fos.flush();
                log.info("Excel file written successfully: {} bytes", excelContent.length);
            } catch (Exception e) {
                log.error("Failed to save Excel file: {}", excelFile.getAbsolutePath(), e);
                throw new IOException("Failed to save Excel file: " + e.getMessage(), e);
            }

            log.info("Excel file saved for cluster {} namespace {}: {}", 
                target.getCluster(), target.getNamespace(), excelFile.getAbsolutePath());

            // Update job as completed
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setExcelFilePath(excelFile.getAbsolutePath());
            job.setCompletedAt(LocalDateTime.now());

            log.info("Conversion job completed for cluster {} namespace {}: {}", 
                target.getCluster(), target.getNamespace(), jobId);

        } catch (Exception e) {
            log.error("Conversion job failed for cluster {} namespace {}: {} - Error: {}", 
                target.getCluster(), target.getNamespace(), jobId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            job.setProgress(job.getProgress());
            job.setCompletedAt(LocalDateTime.now());
        } catch (Throwable t) {
            log.error("CRITICAL: Unexpected error in conversion job for cluster {} namespace {}: {}", 
                target.getCluster(), target.getNamespace(), jobId, t);
            job.setStatus("FAILED");
            job.setErrorMessage("Critical error: " + t.getMessage());
            job.setProgress(job.getProgress());
            job.setCompletedAt(LocalDateTime.now());
        }
    }

    /**
     * Download Excel file for a completed job
     */
    public byte[] downloadExcelFile(String jobId) throws IOException {
        ConversionJobResponse job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        if (!"COMPLETED".equals(job.getStatus())) {
            throw new IllegalStateException("Job is not completed yet: " + job.getStatus());
        }

        if (job.getExcelFilePath() == null) {
            throw new IOException("Excel file path not found");
        }

        File excelFile = new File(job.getExcelFilePath());
        if (!excelFile.exists()) {
            throw new IOException("Excel file not found: " + job.getExcelFilePath());
        }

        return java.nio.file.Files.readAllBytes(excelFile.toPath());
    }

    /**
     * Download all completed Excel files as a ZIP archive
     * Only includes jobs with COMPLETED status
     */
    public byte[] downloadAllExcelFiles() throws IOException {
        List<ConversionJobResponse> completedJobs = jobs.values().stream()
                .filter(job -> "COMPLETED".equals(job.getStatus()))
                .filter(job -> job.getExcelFilePath() != null)
                .collect(Collectors.toList());

        if (completedJobs.isEmpty()) {
            throw new IllegalStateException("No completed jobs found");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ConversionJobResponse job : completedJobs) {
                File excelFile = new File(job.getExcelFilePath());
                if (!excelFile.exists()) {
                    log.warn("Excel file not found for job {}: {}", job.getJobId(), job.getExcelFilePath());
                    continue;
                }

                // Add file to ZIP with its original name
                ZipEntry zipEntry = new ZipEntry(excelFile.getName());
                zos.putNextEntry(zipEntry);
                
                byte[] fileContent = Files.readAllBytes(excelFile.toPath());
                zos.write(fileContent);
                zos.closeEntry();
                
                log.info("Added to ZIP: {} ({} bytes)", excelFile.getName(), fileContent.length);
            }
        }

        byte[] zipContent = baos.toByteArray();
        log.info("Generated ZIP with {} completed jobs, total size: {} bytes", 
                completedJobs.size(), zipContent.length);
        
        return zipContent;
    }

    /**
     * Delete a job
     */
    public void deleteJob(String jobId) {
        ConversionJobResponse job = jobs.remove(jobId);
        if (job != null && job.getExcelFilePath() != null) {
            File excelFile = new File(job.getExcelFilePath());
            if (excelFile.exists()) {
                excelFile.delete();
                log.info("Deleted Excel file for job: {}", jobId);
            }
        }
    }
}
