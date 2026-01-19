package com.nfv.validator.api;

import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.service.CNFChecklistFileParser;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoints for CNF Checklist file operations
 */
@Slf4j
@Path("/kvalidator/api/cnf-checklist")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "CNF Checklist File API", description = "Upload and parse CNF Checklist files (JSON and Excel)")
public class CNFChecklistFileResource {

    @Inject
    CNFChecklistFileParser fileParser;

    /**
     * Upload and parse JSON file
     * Accepts raw file content as request body
     */
    @POST
    @Path("/upload/json")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Upload JSON file", 
               description = "Upload and parse a JSON file containing CNF checklist items")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "File parsed successfully",
                     content = @Content(schema = @Schema(implementation = CNFChecklistUploadResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid file format"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response uploadJsonFile(byte[] fileContent) {
        log.info("Received JSON file upload, size: {} bytes", fileContent != null ? fileContent.length : 0);
        
        try {
            // Validate file
            if (fileContent == null || fileContent.length == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("File is empty or not provided"))
                        .build();
            }
            
            // Parse JSON file
            List<CNFChecklistItem> items = fileParser.parseJsonFile(fileContent);
            
            if (items.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("No valid items found in JSON file"))
                        .build();
            }
            
            log.info("Successfully parsed {} items from JSON file", items.size());
            
            CNFChecklistUploadResponse response = new CNFChecklistUploadResponse();
            response.setSuccess(true);
            response.setMessage("Successfully parsed " + items.size() + " items from JSON file");
            response.setItemCount(items.size());
            response.setItems(items);
            
            return Response.ok(response).build();
            
        } catch (IOException e) {
            log.error("Failed to parse JSON file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Failed to parse JSON file: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error processing JSON file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Unexpected error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Upload and parse Excel file
     * Accepts raw file content as request body
     */
    @POST
    @Path("/upload/excel")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Upload Excel file", 
               description = "Upload and parse an Excel file containing CNF checklist items")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "File parsed successfully",
                     content = @Content(schema = @Schema(implementation = CNFChecklistUploadResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid file format"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response uploadExcelFile(byte[] fileContent) {
        log.info("Received Excel file upload, size: {} bytes", fileContent != null ? fileContent.length : 0);
        
        try {
            // Validate file
            if (fileContent == null || fileContent.length == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("File is empty or not provided"))
                        .build();
            }
            
            // Parse Excel file
            List<CNFChecklistItem> items = fileParser.parseExcelFile(fileContent);
            
            if (items.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("No valid items found in Excel file"))
                        .build();
            }
            
            log.info("Successfully parsed {} items from Excel file", items.size());
            
            CNFChecklistUploadResponse response = new CNFChecklistUploadResponse();
            response.setSuccess(true);
            response.setMessage("Successfully parsed " + items.size() + " items from Excel file");
            response.setItemCount(items.size());
            response.setItems(items);
            
            return Response.ok(response).build();
            
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Failed to parse Excel file: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error processing Excel file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Unexpected error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Download Excel template
     */
    @GET
    @Path("/template/excel")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Download Excel template", 
               description = "Download an Excel template file for CNF checklist with sample data")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Template downloaded successfully"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response downloadExcelTemplate() {
        log.info("Generating Excel template for download");
        
        try {
            byte[] templateData = fileParser.generateExcelTemplate();
            
            return Response.ok(templateData)
                    .header("Content-Disposition", "attachment; filename=\"cnf-checklist-template.xlsx\"")
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to generate Excel template", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(createErrorResponse("Failed to generate Excel template: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error generating Excel template", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(createErrorResponse("Unexpected error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Helper method to create error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return error;
    }

    /**
     * Response for file upload
     */
    public static class CNFChecklistUploadResponse {
        private boolean success;
        private String message;
        private int itemCount;
        private List<CNFChecklistItem> items;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getItemCount() {
            return itemCount;
        }

        public void setItemCount(int itemCount) {
            this.itemCount = itemCount;
        }

        public List<CNFChecklistItem> getItems() {
            return items;
        }

        public void setItems(List<CNFChecklistItem> items) {
            this.items = items;
        }
    }
}
