package com.nfv.validator.model.comparison;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * CNF Checklist comparison result - optimized for CNF validation use case
 * Only returns information for fields specified in the checklist
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CnfComparison {
    
    /**
     * VIM/Cluster name
     */
    @JsonProperty("vimName")
    private String vimName;
    
    /**
     * Kubernetes namespace
     */
    @JsonProperty("namespace")
    private String namespace;
    
    /**
     * List of field validation results
     */
    @JsonProperty("items")
    @Builder.Default
    private List<CnfChecklistResult> items = new ArrayList<>();
    
    /**
     * Summary statistics for this VIM/namespace
     */
    @JsonProperty("summary")
    private CnfSummary summary;
    
    /**
     * Add a checklist result item
     */
    public void addItem(CnfChecklistResult item) {
        items.add(item);
    }
    
    /**
     * Single CNF checklist field validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CnfChecklistResult {
        /**
         * Kubernetes resource kind (e.g., Deployment, ConfigMap)
         */
        @JsonProperty("kind")
        private String kind;
        
        /**
         * Kubernetes object name
         */
        @JsonProperty("objectName")
        private String objectName;
        
        /**
         * Field key path (e.g., spec.replicas, metadata.labels.app)
         */
        @JsonProperty("fieldKey")
        private String fieldKey;
        
        /**
         * Expected value from baseline/checklist (MANO value)
         */
        @JsonProperty("baselineValue")
        private String baselineValue;
        
        /**
         * Actual value from runtime cluster
         */
        @JsonProperty("actualValue")
        private String actualValue;
        
        /**
         * Validation status
         */
        @JsonProperty("status")
        private ValidationStatus status;
        
        /**
         * Additional notes or error message
         */
        @JsonProperty("message")
        private String message;
    }
    
    /**
     * Validation status enum
     */
    public enum ValidationStatus {
        @JsonProperty("MATCH")
        MATCH,              // Values match
        
        @JsonProperty("DIFFERENT")
        DIFFERENT,          // Values differ
        
        @JsonProperty("MISSING_IN_RUNTIME")
        MISSING_IN_RUNTIME, // Field/object not found in runtime
        
        @JsonProperty("ERROR")
        ERROR               // Error during validation
    }
    
    /**
     * Summary statistics for CNF validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CnfSummary {
        /**
         * Total number of fields validated
         */
        @JsonProperty("totalFields")
        private int totalFields;
        
        /**
         * Number of fields that match
         */
        @JsonProperty("matchCount")
        private int matchCount;
        
        /**
         * Number of fields with differences
         */
        @JsonProperty("differenceCount")
        private int differenceCount;
        
        /**
         * Number of fields missing in runtime
         */
        @JsonProperty("missingCount")
        private int missingCount;
        
        /**
         * Number of errors
         */
        @JsonProperty("errorCount")
        private int errorCount;
    }
}
