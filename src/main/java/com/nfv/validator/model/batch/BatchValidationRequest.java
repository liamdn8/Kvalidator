package com.nfv.validator.model.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Root model for batch validation request file
 * Contains multiple validation requests to be executed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationRequest {
    
    /**
     * Version of the batch request format (for future compatibility)
     */
    private String version = "1.0";
    
    /**
     * Description of this batch request
     */
    private String description;
    
    /**
     * List of validation requests to execute
     */
    private List<ValidationRequest> requests = new ArrayList<>();
    
    /**
     * Global settings applied to all requests (can be overridden per request)
     */
    private GlobalSettings settings;
    
    /**
     * Validate all requests in the batch
     */
    public void validate() throws IllegalArgumentException {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Batch request must contain at least one validation request");
        }
        
        // Validate each individual request
        for (int i = 0; i < requests.size(); i++) {
            ValidationRequest request = requests.get(i);
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Validation failed for request #" + (i + 1) + ": " + e.getMessage()
                );
            }
        }
        
        // Check for duplicate request names
        List<String> names = new ArrayList<>();
        for (ValidationRequest request : requests) {
            if (names.contains(request.getName())) {
                throw new IllegalArgumentException(
                    "Duplicate request name found: " + request.getName()
                );
            }
            names.add(request.getName());
        }
    }
    
    /**
     * Global settings for batch execution
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalSettings {
        
        /**
         * Default cluster to use for all requests
         */
        private String defaultCluster;
        
        /**
         * Default validation config file for all requests
         */
        private String defaultConfigFile;
        
        /**
         * Whether to continue execution if one request fails
         */
        private boolean continueOnError = true;
        
        /**
         * Maximum number of requests to execute in parallel
         * 0 or 1 means sequential execution
         */
        private int maxParallelRequests = 1;
        
        /**
         * Output directory for all generated reports
         */
        private String outputDirectory;
        
        /**
         * Whether to generate a summary report for the entire batch
         */
        private boolean generateSummaryReport = true;
        
        /**
         * Path to the batch summary report (if generateSummaryReport is true)
         */
        private String summaryReportPath;
        
        /**
         * Get output directory with timestamp suffix for organizing results
         */
        public String getOutputDirectoryWithTimestamp() {
            String baseDir = outputDirectory != null ? outputDirectory : "batch-results";
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new java.util.Date());
            return "results/" + baseDir + "-" + timestamp;
        }
    }
}
