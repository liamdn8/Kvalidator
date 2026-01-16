package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nfv.validator.model.comparison.CnfComparison;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * JSON response model specifically for CNF Checklist validation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CnfValidationResultJson {
    
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
     * Overall summary statistics across all VIM/namespaces
     */
    @JsonProperty("summary")
    private CnfOverallSummary summary;
    
    /**
     * CNF comparison results grouped by VIM/namespace
     */
    @JsonProperty("results")
    private List<CnfComparison> results;
    
    /**
     * Overall summary for all CNF validations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CnfOverallSummary {
        @JsonProperty("totalVimNamespaces")
        private int totalVimNamespaces;
        
        @JsonProperty("totalFields")
        private int totalFields;
        
        @JsonProperty("totalMatches")
        private int totalMatches;
        
        @JsonProperty("totalDifferences")
        private int totalDifferences;
        
        @JsonProperty("totalMissing")
        private int totalMissing;
        
        @JsonProperty("totalErrors")
        private int totalErrors;
        
        @JsonProperty("executionTimeMs")
        private long executionTimeMs;
    }
}
