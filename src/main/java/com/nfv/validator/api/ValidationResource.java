package com.nfv.validator.api;

import com.nfv.validator.model.api.ValidationJobRequest;
import com.nfv.validator.model.api.ValidationJobResponse;
import com.nfv.validator.model.api.ValidationResultJson;
import com.nfv.validator.service.ValidationJobService;
import com.nfv.validator.service.AsyncValidationExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.File;

/**
 * REST API endpoints for Kubernetes validation service
 */
@Slf4j
@Path("/api/validate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Validation API", description = "Kubernetes validation and comparison endpoints")
public class ValidationResource {
    
    @Inject
    ValidationJobService jobService;
    
    @Inject
    AsyncValidationExecutor executor;
    
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
        
        // Check if baseline mode or namespace comparison
        if (request.getBaselinePath() == null && request.getNamespaces().size() < 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"At least 2 namespaces required for comparison, or provide a baseline\"}")
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
            // Read and return JSON file
            ValidationResultJson results = objectMapper.readValue(jsonFile, ValidationResultJson.class);
            return Response.ok(results).build();
            
        } catch (Exception e) {
            log.error("Failed to read JSON results file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to read results: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
