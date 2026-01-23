package com.nfv.validator.api;

import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.model.cnf.NamespaceInfo;
import com.nfv.validator.model.cnf.YamlToCNFRequest;
import com.nfv.validator.model.cnf.BatchYamlToCNFRequest;
import com.nfv.validator.model.cnf.ConversionJobResponse;
import com.nfv.validator.model.cnf.YamlFileEntry;
import com.nfv.validator.service.CNFChecklistFileParser;
import com.nfv.validator.service.YamlToCNFChecklistConverter;
import com.nfv.validator.service.AsyncConversionExecutor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * REST API endpoints for YAML to CNF Checklist conversion
 */
@Slf4j
@Path("/kvalidator/api/yaml-to-cnf")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "YAML to CNF Checklist API", description = "Convert Kubernetes YAML to CNF Checklist Excel")
public class YamlToCNFResource {

    @Inject
    YamlToCNFChecklistConverter yamlConverter;

    @Inject
    CNFChecklistFileParser fileParser;

    @Inject
    AsyncConversionExecutor conversionExecutor;

    /**
     * Extract namespace information from YAML file (for smart search)
     */
    @POST
    @Path("/extract-namespaces")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Extract namespace information from YAML file",
               description = "Upload YAML file to extract namespace information for smart search")
    @APIResponse(responseCode = "200",
                 description = "Namespaces extracted successfully",
                 content = @Content(schema = @Schema(implementation = NamespaceExtractResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid file or YAML content")
    @APIResponse(responseCode = "500", description = "Server error")
    public Response extractNamespaces(@MultipartForm YamlFileUpload upload) {
        try {
            log.info("Extracting namespaces from uploaded YAML file: {}", upload.fileName);
            
            // Read file content
            String yamlContent = Files.readString(upload.file.uploadedFile(), StandardCharsets.UTF_8);
            
            // Extract namespaces
            List<NamespaceInfo> namespaces = yamlConverter.extractNamespaces(yamlContent);
            
            NamespaceExtractResponse response = NamespaceExtractResponse.builder()
                .success(true)
                .message(String.format("Found %d namespace(s) in YAML file", namespaces.size()))
                .namespaces(namespaces)
                .build();
            
            return Response.ok(response).build();
            
        } catch (IOException e) {
            log.error("Failed to extract namespaces from YAML", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, "Failed to parse YAML: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error extracting namespaces", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Convert YAML to CNF Checklist Excel file
     */
    @POST
    @Path("/convert-to-excel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Convert YAML to CNF Checklist Excel",
               description = "Convert Kubernetes YAML to CNF Checklist Excel file")
    @APIResponse(responseCode = "200",
                 description = "Excel file generated successfully",
                 content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    @APIResponse(responseCode = "400", description = "Invalid request or YAML content")
    @APIResponse(responseCode = "500", description = "Server error")
    public Response convertToExcel(YamlToCNFRequest request) {
        try {
            log.info("Converting YAML to CNF checklist Excel for vimName: {}", request.getVimName());
            
            // Validate request
            request.validate();
            
            // Convert YAML to checklist items
            List<CNFChecklistItem> items = yamlConverter.convertToCNFChecklist(
                request.getVimName(),
                request.getYamlContent(),
                request.getNamespaces(),
                request.getImportantFields()
            );
            
            if (items.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse(false, "No checklist items generated from YAML"))
                        .build();
            }
            
            // Generate Excel file
            byte[] excelContent = fileParser.generateExcelFromItems(items);
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = String.format("cnf-checklist-%s-%s.xlsx", 
                request.getVimName().replaceAll("[^a-zA-Z0-9-]", "_"), timestamp);
            
            return Response.ok(excelContent)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("X-Item-Count", String.valueOf(items.size()))
                    .build();
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        } catch (IOException e) {
            log.error("Failed to convert YAML to Excel", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, "Failed to process YAML: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error converting YAML to Excel", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Submit batch conversion job (multiple YAML files)
     */
    @POST
    @Path("/batch/submit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Submit batch YAML to CNF conversion job",
               description = "Convert multiple Kubernetes YAML files to CNF Checklist (async). Creates one job per target namespace.")
    @APIResponse(responseCode = "200",
                 description = "Conversion jobs submitted successfully",
                 content = @Content(schema = @Schema(implementation = ConversionJobResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "500", description = "Server error")
    public Response submitBatchConversion(BatchYamlToCNFRequest request) {
        try {
            log.info("Submitting batch conversion jobs for {} targets with {} files", 
                    request.getTargets() != null ? request.getTargets().size() : 0,
                    request.getYamlFiles() != null ? request.getYamlFiles().size() : 0);
            
            List<ConversionJobResponse> jobs = conversionExecutor.submitConversionJob(request);
            
            return Response.ok(jobs).build();
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid batch conversion request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error submitting batch conversion", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get conversion job status
     */
    @GET
    @Path("/batch/jobs/{jobId}")
    @Operation(summary = "Get conversion job status",
               description = "Get status and progress of a conversion job")
    @APIResponse(responseCode = "200",
                 description = "Job status retrieved successfully",
                 content = @Content(schema = @Schema(implementation = ConversionJobResponse.class)))
    @APIResponse(responseCode = "404", description = "Job not found")
    public Response getJobStatus(@PathParam("jobId") String jobId) {
        try {
            ConversionJobResponse job = conversionExecutor.getJobStatus(jobId);
            return Response.ok(job).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        }
    }

    /**
     * Download Excel file for completed conversion job
     */
    @GET
    @Path("/batch/jobs/{jobId}/download")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Download Excel file for conversion job",
               description = "Download the generated CNF Checklist Excel file")
    @APIResponse(responseCode = "200",
                 description = "Excel file downloaded successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    @APIResponse(responseCode = "400", description = "Job not completed yet")
    public Response downloadJobExcel(@PathParam("jobId") String jobId) {
        try {
            byte[] excelContent = conversionExecutor.downloadExcelFile(jobId);
            
            String filename = String.format("cnf-checklist-%s.xlsx", jobId);
            
            return Response.ok(excelContent)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to download Excel file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Failed to download file: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Download all completed Excel files as ZIP
     */
    @GET
    @Path("/batch/jobs/download-all")
    @Produces("application/zip")
    @Operation(summary = "Download all completed conversion jobs as ZIP",
               description = "Download all generated CNF Checklist Excel files in a single ZIP archive")
    @APIResponse(responseCode = "200",
                 description = "ZIP file downloaded successfully")
    @APIResponse(responseCode = "400", description = "No completed jobs found")
    public Response downloadAllJobsAsZip() {
        try {
            byte[] zipContent = conversionExecutor.downloadAllExcelFiles();
            
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = String.format("cnf-checklists-all-%s.zip", timestamp);
            
            return Response.ok(zipContent)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
                    
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to download all Excel files as ZIP", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Failed to download ZIP: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all conversion jobs
     */
    @GET
    @Path("/batch/jobs")
    @Operation(summary = "Get all conversion jobs",
               description = "Get list of all conversion jobs")
    @APIResponse(responseCode = "200",
                 description = "Jobs retrieved successfully")
    public Response getAllJobs() {
        List<ConversionJobResponse> jobs = conversionExecutor.getAllJobs();
        return Response.ok(jobs).build();
    }

    /**
     * Delete a conversion job
     */
    @DELETE
    @Path("/batch/jobs/{jobId}")
    @Operation(summary = "Delete conversion job",
               description = "Delete a conversion job and its files")
    @APIResponse(responseCode = "200", description = "Job deleted successfully")
    public Response deleteJob(@PathParam("jobId") String jobId) {
        try {
            conversionExecutor.deleteJob(jobId);
            return Response.ok(new ErrorResponse(true, "Job deleted successfully")).build();
        } catch (Exception e) {
            log.error("Failed to delete job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Failed to delete job: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Extract namespaces from multiple YAML files (batch)
     */
    @POST
    @Path("/batch/extract-namespaces")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Extract namespaces from multiple YAML files",
               description = "Extract namespace information from multiple YAML files for smart search")
    @APIResponse(responseCode = "200",
                 description = "Namespaces extracted successfully",
                 content = @Content(schema = @Schema(implementation = NamespaceExtractResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    public Response extractNamespacesFromBatch(BatchNamespaceExtractRequest request) {
        try {
            log.info("Extracting namespaces from {} YAML files", request.yamlFiles.size());
            
            List<NamespaceInfo> namespaces = yamlConverter.extractNamespacesFromMultipleFiles(request.yamlFiles);
            
            NamespaceExtractResponse response = NamespaceExtractResponse.builder()
                .success(true)
                .message(String.format("Found %d namespace(s) across %d YAML files", 
                        namespaces.size(), request.yamlFiles.size()))
                .namespaces(namespaces)
                .build();
            
            return Response.ok(response).build();
            
        } catch (IOException e) {
            log.error("Failed to extract namespaces from batch", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(false, "Failed to parse YAML: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error extracting namespaces", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(false, "Server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Request for batch namespace extraction
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchNamespaceExtractRequest {
        public List<YamlFileEntry> yamlFiles;
    }

    /**
     * Multipart form for YAML file upload
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YamlFileUpload {
        @PartType(MediaType.APPLICATION_OCTET_STREAM)
        public FileUpload file;
        
        @PartType(MediaType.TEXT_PLAIN)
        public String fileName;
    }

    /**
     * Response for namespace extraction
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Response for namespace extraction")
    public static class NamespaceExtractResponse {
        private boolean success;
        private String message;
        private List<NamespaceInfo> namespaces;
    }

    /**
     * Error response
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Error response")
    public static class ErrorResponse {
        private boolean success;
        private String message;
    }
}
