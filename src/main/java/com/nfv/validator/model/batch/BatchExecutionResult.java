package com.nfv.validator.model.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of executing a batch validation request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecutionResult {
    
    /**
     * Start time of batch execution
     */
    private LocalDateTime startTime;
    
    /**
     * End time of batch execution
     */
    private LocalDateTime endTime;
    
    /**
     * Total number of requests in the batch
     */
    private int totalRequests;
    
    /**
     * Number of successfully executed requests
     */
    private int successfulRequests;
    
    /**
     * Number of failed requests
     */
    private int failedRequests;
    
    /**
     * Results for each individual request
     */
    private List<RequestResult> requestResults = new ArrayList<>();
    
    /**
     * Add a request result
     */
    public void addRequestResult(RequestResult result) {
        if (requestResults == null) {
            requestResults = new ArrayList<>();
        }
        requestResults.add(result);
        
        if (result.isSuccess()) {
            successfulRequests++;
        } else {
            failedRequests++;
        }
    }
    
    /**
     * Check if all requests were successful
     */
    public boolean isAllSuccessful() {
        return failedRequests == 0;
    }
    
    /**
     * Result of a single request execution
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestResult {
        
        /**
         * Request name
         */
        private String requestName;
        
        /**
         * Whether the request executed successfully
         */
        private boolean success;
        
        /**
         * Error message if failed
         */
        private String errorMessage;
        
        /**
         * Exception stack trace if failed
         */
        private String stackTrace;
        
        /**
         * Output file path if generated
         */
        private String outputPath;
        
        /**
         * Execution time in milliseconds
         */
        private long executionTimeMs;
        
        /**
         * Number of objects compared (for summary)
         */
        private int objectsCompared;
        
        /**
         * Number of differences found (for summary)
         */
        private int differencesFound;
    }
}
