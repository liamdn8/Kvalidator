package com.nfv.validator.api;

import com.nfv.validator.service.ValidationConfigService;
import com.nfv.validator.config.ValidationConfig;
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
import java.util.HashMap;
import java.util.Map;

/**
 * REST API for managing validation configuration
 */
@Slf4j
@Path("/kvalidator/api/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Validation Config API", description = "Manage validation configuration and ignore rules")
public class ValidationConfigResource {
    
    @Inject
    ValidationConfigService configService;
    
    /**
     * Get current validation configuration
     */
    @GET
    @Operation(summary = "Get current validation config", 
               description = "Retrieve the current validation configuration with all ignore rules")
    @APIResponses({
        @APIResponse(responseCode = "200", 
                     description = "Config retrieved successfully",
                     content = @Content(schema = @Schema(implementation = ValidationConfig.class))),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response getCurrentConfig() {
        try {
            ValidationConfig config = configService.loadConfig();
            log.info("Retrieved validation config with {} ignore rules", 
                    config.getIgnoreFields().size());
            return Response.ok(config).build();
        } catch (Exception e) {
            log.error("Failed to get validation config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to load config: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Update validation configuration
     */
    @PUT
    @Operation(summary = "Update validation config", 
               description = "Update the validation configuration with new ignore rules")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config updated successfully"),
        @APIResponse(responseCode = "400", description = "Invalid config"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response updateConfig(ValidationConfig config) {
        try {
            if (config == null || config.getIgnoreFields() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid config: ignoreFields is required"))
                        .build();
            }
            
            configService.saveConfig(config);
            log.info("Updated validation config with {} ignore rules", 
                    config.getIgnoreFields().size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration updated successfully");
            response.put("ignoreFieldsCount", config.getIgnoreFields().size());
            
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to update validation config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to save config: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Add ignore field to configuration
     */
    @POST
    @Path("/ignore-field")
    @Operation(summary = "Add ignore field", 
               description = "Add a new field to the ignore list")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Field added successfully"),
        @APIResponse(responseCode = "400", description = "Invalid field"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response addIgnoreField(Map<String, String> request) {
        try {
            String fieldPath = request.get("fieldPath");
            if (fieldPath == null || fieldPath.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "fieldPath is required"))
                        .build();
            }
            
            ValidationConfig config = configService.loadConfig();
            config.addIgnoreField(fieldPath.trim());
            configService.saveConfig(config);
            
            log.info("Added ignore field: {}", fieldPath);
            
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Field added successfully",
                    "fieldPath", fieldPath,
                    "totalIgnoreFields", config.getIgnoreFields().size()
            )).build();
        } catch (Exception e) {
            log.error("Failed to add ignore field", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to add field: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Remove ignore field from configuration
     */
    @DELETE
    @Path("/ignore-field")
    @Operation(summary = "Remove ignore field", 
               description = "Remove a field from the ignore list")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Field removed successfully"),
        @APIResponse(responseCode = "400", description = "Invalid field"),
        @APIResponse(responseCode = "404", description = "Field not found"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response removeIgnoreField(@QueryParam("fieldPath") String fieldPath) {
        try {
            if (fieldPath == null || fieldPath.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "fieldPath query parameter is required"))
                        .build();
            }
            
            ValidationConfig config = configService.loadConfig();
            boolean removed = config.getIgnoreFields().remove(fieldPath.trim());
            
            if (!removed) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Field not found in ignore list"))
                        .build();
            }
            
            configService.saveConfig(config);
            log.info("Removed ignore field: {}", fieldPath);
            
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Field removed successfully",
                    "fieldPath", fieldPath,
                    "totalIgnoreFields", config.getIgnoreFields().size()
            )).build();
        } catch (Exception e) {
            log.error("Failed to remove ignore field", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to remove field: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Export validation configuration as YAML
     */
    @GET
    @Path("/export")
    @Produces("application/x-yaml")
    @Operation(summary = "Export config as YAML", 
               description = "Download the current validation configuration as a YAML file")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config exported successfully"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response exportConfig() {
        try {
            String yamlContent = configService.exportConfigAsYaml();
            
            return Response.ok(yamlContent)
                    .header("Content-Disposition", "attachment; filename=\"validation-config.yaml\"")
                    .build();
        } catch (Exception e) {
            log.error("Failed to export validation config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to export config: " + e.getMessage() + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
    
    /**
     * Import validation configuration from YAML
     */
    @POST
    @Path("/import")
    @Consumes("application/x-yaml")
    @Operation(summary = "Import config from YAML", 
               description = "Upload and import a validation configuration YAML file")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config imported successfully"),
        @APIResponse(responseCode = "400", description = "Invalid YAML"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response importConfig(String yamlContent) {
        try {
            if (yamlContent == null || yamlContent.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "YAML content is required"))
                        .build();
            }
            
            ValidationConfig config = configService.importConfigFromYaml(yamlContent);
            configService.saveConfig(config);
            
            log.info("Imported validation config with {} ignore rules", 
                    config.getIgnoreFields().size());
            
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Configuration imported successfully",
                    "ignoreFieldsCount", config.getIgnoreFields().size()
            )).build();
        } catch (Exception e) {
            log.error("Failed to import validation config", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to parse YAML: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Reset to default configuration
     */
    @POST
    @Path("/reset")
    @Operation(summary = "Reset to default config", 
               description = "Reset validation configuration to default settings")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Config reset successfully"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    public Response resetToDefault() {
        try {
            ValidationConfig config = configService.loadDefaultConfig();
            configService.saveConfig(config);
            
            log.info("Reset validation config to default with {} ignore rules", 
                    config.getIgnoreFields().size());
            
            return Response.ok(Map.of(
                    "success", true,
                    "message", "Configuration reset to default successfully",
                    "ignoreFieldsCount", config.getIgnoreFields().size()
            )).build();
        } catch (Exception e) {
            log.error("Failed to reset validation config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to reset config: " + e.getMessage()))
                    .build();
        }
    }
}
