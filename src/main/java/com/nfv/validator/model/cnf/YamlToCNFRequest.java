package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for converting Kubernetes YAML to CNF Checklist Excel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YamlToCNFRequest {
    
    /**
     * VIM name / Cluster identifier (e.g., "vim-hanoi", "vim-hcm")
     */
    private String vimName;
    
    /**
     * List of namespaces to extract from YAML files (optional - if empty, extract all)
     */
    private List<String> namespaces;
    
    /**
     * YAML content (can contain multiple documents separated by ---)
     */
    private String yamlContent;
    
    /**
     * Important fields to extract for each resource type
     * If null/empty, will use default important fields
     */
    private List<String> importantFields;
    
    /**
     * Validate request
     */
    public void validate() throws IllegalArgumentException {
        if (vimName == null || vimName.trim().isEmpty()) {
            throw new IllegalArgumentException("VIM name is required");
        }
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("YAML content is required");
        }
    }
}
