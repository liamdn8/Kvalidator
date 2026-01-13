package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress information for a running job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobProgress {
    
    /**
     * Current step being executed
     */
    @JsonProperty("currentStep")
    private String currentStep;
    
    /**
     * Progress percentage (0-100)
     */
    @JsonProperty("percentage")
    private Integer percentage;
    
    /**
     * Number of namespaces processed
     */
    @JsonProperty("namespacesProcessed")
    private Integer namespacesProcessed;
    
    /**
     * Total namespaces to process
     */
    @JsonProperty("totalNamespaces")
    private Integer totalNamespaces;
    
    /**
     * Number of objects compared so far
     */
    @JsonProperty("objectsCompared")
    private Integer objectsCompared;
}
