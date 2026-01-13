package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nfv.validator.model.comparison.NamespaceComparison;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * JSON export model for validation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultJson {
    
    /**
     * Job metadata
     */
    @JsonProperty("jobId")
    private String jobId;
    
    @JsonProperty("submittedAt")
    private Instant submittedAt;
    
    @JsonProperty("completedAt")
    private Instant completedAt;
    
    @JsonProperty("description")
    private String description;
    
    /**
     * Summary statistics
     */
    @JsonProperty("summary")
    private SummaryStats summary;
    
    /**
     * Detailed comparison results by namespace pair
     */
    @JsonProperty("comparisons")
    private Map<String, NamespaceComparison> comparisons;
    
    /**
     * Summary statistics inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        @JsonProperty("totalObjects")
        private int totalObjects;
        
        @JsonProperty("totalDifferences")
        private int totalDifferences;
        
        @JsonProperty("namespacePairs")
        private int namespacePairs;
        
        @JsonProperty("executionTimeMs")
        private long executionTimeMs;
    }
}
