package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about a namespace found in YAML files
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceInfo {
    
    /**
     * Namespace name
     */
    private String name;
    
    /**
     * Number of resources in this namespace
     */
    private int resourceCount;
    
    /**
     * Resource kinds found (comma-separated, e.g., "Deployment, Service, ConfigMap")
     */
    private String resourceKinds;
}
