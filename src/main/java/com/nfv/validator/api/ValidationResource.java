package com.nfv.validator.api;

import com.nfv.validator.model.api.ValidationJobRequest;
import com.nfv.validator.model.api.ValidationJobResponse;
import com.nfv.validator.model.api.ValidationResultJson;
import com.nfv.validator.service.ValidationJobService;
import com.nfv.validator.service.AsyncValidationExecutor;
import com.nfv.validator.service.CNFChecklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nfv.validator.model.cnf.CNFChecklistRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nfv.validator.model.batch.BatchValidationRequest;

/**
 * REST API endpoints for Kubernetes validation service
 */
@Slf4j
@Path("/kvalidator/api/validate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Validation API", description = "Kubernetes validation and comparison endpoints")
public class ValidationResource {
    
    @Inject
    ValidationJobService jobService;
    
    @Inject
    AsyncValidationExecutor executor;
    
    @Inject
    CNFChecklistService cnfChecklistService;
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Submit a new validation job
     */
    @POST
    @Operation(summary = "Submit validation job", 
               description = "Submit a new Kubernetes validation job and get job ID")
    @APIResponses({
        @APIResponse(responseCode = "201", 
                     description = "Job created successfully",
                     content = @Content(schema = @Schema(implementation = ValidationJobResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response submitValidation(ValidationJobRequest request) {
        log.info("Received validation request for namespaces: {}", request.getNamespaces());
        
        // Validate request
        if (request.getNamespaces() == null || request.getNamespaces().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"At least one namespace is required\"}")
                    .build();
        }
        
        try {
            // Create job
            ValidationJobResponse job = jobService.createJob(request);
            
            // Start async execution
            executor.executeAsync(job.getJobId(), request);
            
            return Response.status(Response.Status.CREATED)
                    .entity(job)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to submit validation job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Submit a new CNF checklist validation job (Async)
     */
    @POST
    @Path("/cnf-checklist")
    @Operation(summary = "Submit CNF checklist validation (Async)", 
               description = "Validate Kubernetes configurations against CNF checklist and get job ID for async processing")
    @APIResponses({
        @APIResponse(responseCode = "201", 
                     description = "Job created successfully",
                     content = @Content(schema = @Schema(implementation = ValidationJobResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response submitCNFChecklistValidation(CNFChecklistRequest request) {
        log.info("Received CNF checklist validation request with {} items", 
            request.getItems() != null ? request.getItems().size() : 0);
        
        // Validate request
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"At least one checklist item is required\"}")
                    .build();
        }
        
        try {
            // Validate checklist items
            request.validate();
            
            // Convert CNF checklist to batch validation request using flattened approach
            // This allows using the same output format and UI as batch validation
            BatchValidationRequest batchRequest = cnfChecklistService.convertToBatchRequestFlattened(request);
            
            // Create job for batch
            ValidationJobResponse job = jobService.createBatchJob(batchRequest);
            
            // Start async execution using batch executor
            executor.executeBatchAsync(job.getJobId(), batchRequest);
            
            return Response.status(Response.Status.CREATED)
                    .entity(job)
                    .build();
                    
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CNF checklist request: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            log.error("Failed to submit CNF checklist validation job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Submit a new CNF checklist validation and wait for result (Sync)
     */
    @POST
    @Path("/cnf-checklist/sync")
    @Operation(summary = "Submit CNF checklist validation (Sync)", 
               description = "Validate Kubernetes configurations against CNF checklist and return results immediately")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "Validation completed successfully",
                     content = @Content(schema = @Schema(implementation = ValidationResultJson.class))),
        @APIResponse(responseCode = "400", description = "Invalid request"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response submitCNFChecklistValidationSync(CNFChecklistRequest request) {
        log.info("Received SYNC CNF checklist validation request with {} items", 
            request.getItems() != null ? request.getItems().size() : 0);
        
        // Validate request
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"At least one checklist item is required\"}")
                    .build();
        }
        
        try {
            // Note: For sync mode, we create a temporary batch job and execute it synchronously
            // This is not recommended for production as it blocks the HTTP request
            // Use async endpoint (/cnf-checklist) instead
            request.validate();
            
            // Convert to batch request using flattened approach
            BatchValidationRequest batchRequest = cnfChecklistService.convertToBatchRequestFlattened(request);
            
            // Create temporary job
            ValidationJobResponse job = jobService.createBatchJob(batchRequest);
            
            // Execute synchronously (blocking)
            executor.executeBatchAsync(job.getJobId(), batchRequest);
            
            // Poll for completion (with timeout)
            int maxAttempts = 60; // 60 seconds timeout
            for (int i = 0; i < maxAttempts; i++) {
                Thread.sleep(1000);
                ValidationJobResponse updatedJob = jobService.getJob(job.getJobId());
                if (updatedJob.getStatus() == com.nfv.validator.model.api.JobStatus.COMPLETED) {
                    // Return batch job response
                    return Response.ok(updatedJob).build();
                } else if (updatedJob.getStatus() == com.nfv.validator.model.api.JobStatus.FAILED) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("{\"error\": \"Validation failed: " + updatedJob.getMessage() + "\"}")
                            .build();
                }
            }
            
            return Response.status(Response.Status.REQUEST_TIMEOUT)
                    .entity("{\"error\": \"Validation timed out\"}")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CNF checklist request: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute CNF checklist validation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Submit a new batch validation job
     */
    @POST
    @Path("/batch")
    @Operation(summary = "Submit batch validation job", 
               description = "Submit a new batch validation job and get job ID")
    @APIResponses({
        @APIResponse(responseCode = "201", 
                     description = "Job created successfully",
                     content = @Content(schema = @Schema(implementation = ValidationJobResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response submitBatchValidation(BatchValidationRequest request) {
        log.info("Received batch validation request with {} items", request.getRequests().size());
        
        // Validate request
        if (request.getRequests() == null || request.getRequests().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"At least one validation request is required\"}")
                    .build();
        }
        
        try {
            // Create job for batch
            ValidationJobResponse job = jobService.createBatchJob(request);
            
            // Start async execution
            executor.executeBatchAsync(job.getJobId(), request);
            
            return Response.status(Response.Status.CREATED)
                    .entity(job)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to submit batch validation job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Get validation job status
     */
    @GET
    @Path("/{jobId}")
    @Operation(summary = "Get job status", 
               description = "Get current status and progress of a validation job")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "Job status retrieved",
                     content = @Content(schema = @Schema(implementation = ValidationJobResponse.class))),
        @APIResponse(responseCode = "404", description = "Job not found")
    })
    public Response getJobStatus(@PathParam("jobId") String jobId) {
        log.debug("Getting status for job: {}", jobId);
        
        ValidationJobResponse job = jobService.getJob(jobId);
        
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Job not found\"}")
                    .build();
        }
        
        return Response.ok(job).build();
    }
    
    /**
     * Download Excel report for completed job
     */
    @GET
    @Path("/{jobId}/download")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Download Excel report", 
               description = "Download Excel validation report for a completed job")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Excel file downloaded"),
        @APIResponse(responseCode = "404", description = "Job or report not found"),
        @APIResponse(responseCode = "425", description = "Job not yet completed")
    })
    public Response downloadExcelReport(@PathParam("jobId") String jobId) {
        log.info("Downloading Excel report for job: {}", jobId);
        
        ValidationJobResponse job = jobService.getJob(jobId);
        
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Job not found\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        if (job.getStatus() != com.nfv.validator.model.api.JobStatus.COMPLETED) {
            return Response.status(425) // Too Early
                    .entity("{\"error\": \"Job not yet completed\", \"status\": \"" + job.getStatus() + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        File excelFile = jobService.getExcelReportFile(jobId);
        
        if (excelFile == null || !excelFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Excel report not found\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        return Response.ok(excelFile)
                .header("Content-Disposition", "attachment; filename=\"validation-report-" + jobId + ".xlsx\"")
                .build();
    }
    
    /**
     * Get JSON results for completed job
     */
    @GET
    @Path("/{jobId}/json")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get JSON results", 
               description = "Get validation results in JSON format for web display")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "JSON results retrieved",
                     content = @Content(schema = @Schema(implementation = ValidationResultJson.class))),
        @APIResponse(responseCode = "404", description = "Job or results not found"),
        @APIResponse(responseCode = "425", description = "Job not yet completed")
    })
    public Response getJsonResults(@PathParam("jobId") String jobId) {
        log.info("Getting JSON results for job: {}", jobId);
        
        ValidationJobResponse job = jobService.getJob(jobId);
        
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Job not found\"}")
                    .build();
        }
        
        if (job.getStatus() != com.nfv.validator.model.api.JobStatus.COMPLETED) {
            return Response.status(425) // Too Early
                    .entity("{\"error\": \"Job not yet completed\", \"status\": \"" + job.getStatus() + "\"}")
                    .build();
        }
        
        File jsonFile = jobService.getJsonResultsFile(jobId);
        
        if (jsonFile == null || !jsonFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"JSON results not found\"}")
                    .build();
        }
        
        try {
            // Read and return JSON file as-is (supports both CNF and standard formats)
            String jsonContent = Files.readString(jsonFile.toPath());
            return Response.ok(jsonContent)
                    .type("application/json")
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to read JSON results file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to read results: " + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Get CNF-specific JSON results for completed job
     */
    @GET
    @Path("/{jobId}/cnf-json")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get CNF JSON results", 
               description = "Get CNF checklist validation results in optimized JSON format for web display")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "CNF JSON results retrieved",
                     content = @Content(schema = @Schema(implementation = com.nfv.validator.model.api.CnfValidationResultJson.class))),
        @APIResponse(responseCode = "404", description = "Job or results not found"),
        @APIResponse(responseCode = "425", description = "Job not yet completed")
    })
    public Response getCnfJsonResults(@PathParam("jobId") String jobId) {
        log.info("Getting CNF JSON results for job: {}", jobId);
        
        ValidationJobResponse job = jobService.getJob(jobId);
        
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Job not found\"}")
                    .build();
        }
        
        if (job.getStatus() != com.nfv.validator.model.api.JobStatus.COMPLETED) {
            return Response.status(425) // Too Early
                    .entity("{\"error\": \"Job not yet completed\", \"status\": \"" + job.getStatus() + "\"}")
                    .build();
        }
        
        // Try to get CNF-specific JSON file first
        java.nio.file.Path resultsDir = jobService.getJobResultsDirectory(jobId);
        java.io.File cnfJsonFile = resultsDir.resolve("cnf-results.json").toFile();
        
        if (!cnfJsonFile.exists()) {
            // Fall back to standard JSON
            log.warn("CNF JSON file not found for job {}, falling back to standard JSON", jobId);
            return getJsonResults(jobId);
        }
        
        try {
            // Read and return CNF JSON file
            com.nfv.validator.model.api.CnfValidationResultJson results = 
                objectMapper.readValue(cnfJsonFile, com.nfv.validator.model.api.CnfValidationResultJson.class);
            return Response.ok(results).build();
            
        } catch (Exception e) {
            log.error("Failed to read CNF JSON results file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to read CNF results: " + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Get individual jobs for a batch job
     */
    @GET
    @Path("/batch/{batchJobId}/jobs")
    @Operation(summary = "Get individual jobs for batch", 
               description = "Get list of individual job IDs for a batch validation job")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Individual jobs list retrieved"),
        @APIResponse(responseCode = "404", description = "Batch job not found")
    })
    public Response getBatchIndividualJobs(@PathParam("batchJobId") String batchJobId) {
        log.info("Getting individual jobs for batch: {}", batchJobId);
        
        ValidationJobResponse batchJob = jobService.getJob(batchJobId);
        if (batchJob == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Batch job not found\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        // Find individual jobs that belong to this batch
        // Individual jobs have IDs like: {batchJobId}-1, {batchJobId}-2, etc.
        List<String> individualJobIds = jobService.getIndividualJobsForBatch(batchJobId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("batchJobId", batchJobId);
        response.put("individualJobs", individualJobIds);
        response.put("totalJobs", individualJobIds.size());
        
        return Response.ok(response).build();
    }
    
    /**
     * Export all batch results as ZIP file
     */
    @GET
    @Path("/batch/{jobId}/export-zip")
    @Produces("application/zip")
    @Operation(summary = "Export batch results as ZIP", 
               description = "Download all Excel reports from a batch validation job as a ZIP file")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "ZIP file downloaded"),
        @APIResponse(responseCode = "404", description = "Job or results not found"),
        @APIResponse(responseCode = "425", description = "Job not yet completed")
    })
    public Response exportBatchResultsZip(@PathParam("jobId") String jobId) {
        log.info("Exporting batch results as ZIP for job: {}", jobId);
        
        ValidationJobResponse job = jobService.getJob(jobId);
        
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Job not found\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        if (job.getStatus() != com.nfv.validator.model.api.JobStatus.COMPLETED) {
            return Response.status(425) // Too Early
                    .entity("{\"error\": \"Job not yet completed\", \"status\": \"" + job.getStatus() + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        try {
            // Use service to create ZIP file
            File zipFile = jobService.exportBatchResultsAsZip(jobId);
            
            // Return ZIP file
            return Response.ok(zipFile)
                    .header("Content-Disposition", "attachment; filename=\"batch-results-" + jobId + ".zip\"")
                    .build();
                    
        } catch (IllegalStateException e) {
            log.warn("Batch results not found for job {}: {}", jobId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Failed to export batch results as ZIP", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to create ZIP file: " + e.getMessage() + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
