package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Target namespace with cluster information for YAML to CNF conversion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceTarget {
    
    /**
     * Cluster name / VIM name (e.g., "vim-hanoi", "cluster-1")
     */
    private String cluster;
    
    /**
     * Namespace name (e.g., "app-dev", "kube-system")
     */
    private String namespace;
    
    /**
     * Get unique key for this target (cluster:namespace)
     */
    public String getUniqueKey() {
        return cluster + ":" + namespace;
    }
}
