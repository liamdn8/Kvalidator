package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch request for converting multiple YAML files to CNF Checklist
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchYamlToCNFRequest {
    
    /**
     * List of cluster-namespace pairs to create conversion jobs for
     * Each pair will get its own conversion job with the same YAML files
     * Format: [{cluster: "cluster-1", namespace: "app-dev"}, ...]
     */
    private List<NamespaceTarget> targets;
    
    /**
     * List of YAML files to process
     */
    private List<YamlFileEntry> yamlFiles;
    
    /**
     * List of namespaces to filter from YAML files (optional)
     * If not specified, will use all namespaces found in YAML files
     */
    private List<String> namespacesFilter;
    
    /**
     * Flatten mode: "flat" or "semantic"
     * - flat: Traditional flatten (YamlDataCollector)
     * - semantic: Semantic flatten preserving structure (YamlDataCollectorV2)
     */
    private String flattenMode;
    
    /**
     * Important fields to extract (optional, uses defaults if empty)
     */
    private List<String> importantFields;
    
    /**
     * Job description
     */
    private String description;
    
    /**
     * Validate request
     */
    public void validate() throws IllegalArgumentException {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("At least one target (cluster-namespace pair) is required");
        }
        
        // Validate each target
        for (NamespaceTarget target : targets) {
            if (target.getCluster() == null || target.getCluster().trim().isEmpty()) {
                throw new IllegalArgumentException("Cluster name is required for all targets");
            }
            if (target.getNamespace() == null || target.getNamespace().trim().isEmpty()) {
                throw new IllegalArgumentException("Namespace is required for all targets");
            }
        }
        
        if (yamlFiles == null || yamlFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one YAML file is required");
        }
        
        // Validate each file
        for (int i = 0; i < yamlFiles.size(); i++) {
            YamlFileEntry file = yamlFiles.get(i);
            if (file.getYamlContent() == null || file.getYamlContent().trim().isEmpty()) {
                throw new IllegalArgumentException("YAML content is empty for file " + (i + 1));
            }
        }
        
        // Validate flatten mode
        if (flattenMode == null || flattenMode.trim().isEmpty()) {
            flattenMode = "flat"; // Default
        } else if (!flattenMode.equals("flat") && !flattenMode.equals("semantic")) {
            throw new IllegalArgumentException("Flatten mode must be 'flat' or 'semantic'");
        }
    }
    
    /**
     * Get flatten mode with default
     */
    public String getFlattenMode() {
        return flattenMode != null && !flattenMode.trim().isEmpty() ? flattenMode : "flat";
    }
}
