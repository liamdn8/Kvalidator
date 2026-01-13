package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for validation job submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationJobRequest {
    
    /**
     * List of namespace targets to compare
     * Format: "namespace" or "cluster/namespace"
     */
    @JsonProperty("namespaces")
    private List<String> namespaces;
    
    /**
     * Optional baseline YAML file path (server-side file)
     */
    @JsonProperty("baselinePath")
    private String baselinePath;
    
    /**
     * Optional baseline objects (client-side preprocessed)
     * Map of object name to flattened key-value pairs
     */
    @JsonProperty("baselineObjects")
    private java.util.Map<String, java.util.Map<String, String>> baselineObjects;
    
    /**
     * Baseline cluster name (for client-side baseline)
     */
    @JsonProperty("baselineCluster")
    private String baselineCluster;
    
    /**
     * Baseline namespace name (for client-side baseline)
     */
    @JsonProperty("baselineNamespace")
    private String baselineNamespace;
    
    /**
     * Optional cluster name (default: current context)
     */
    @JsonProperty("cluster")
    private String cluster;
    
    /**
     * Optional resource kinds to compare (default: all)
     */
    @JsonProperty("kinds")
    private List<String> kinds;
    
    /**
     * Optional custom validation config file path
     */
    @JsonProperty("configFile")
    private String configFile;
    
    /**
     * Optional description for this validation job
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * Whether to export Excel report (default: true)
     */
    @JsonProperty("exportExcel")
    private Boolean exportExcel;
}
