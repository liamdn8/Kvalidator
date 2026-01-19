package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nfv.validator.model.FlatNamespaceModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
     * Optional baseline YAML content (client-side content)
     * Used when user provides YAML content directly from web UI
     */
    @JsonProperty("baselineYamlContent")
    private String baselineYamlContent;
    
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
    
    /**
     * Custom ignore fields for this validation (optional)
     * These will be merged with default ignore rules from config
     */
    @JsonProperty("ignoreFields")
    private List<String> ignoreFields;
    
    /**
     * CNF Checklist request (transient, used internally)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Object cnfChecklistRequest;
    
    /**
     * Flattened baseline model for direct comparison (internal use)
     * Used when converting CNF checklists to avoid YAML processing
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Map<String, FlatNamespaceModel> flattenedBaseline;
}
