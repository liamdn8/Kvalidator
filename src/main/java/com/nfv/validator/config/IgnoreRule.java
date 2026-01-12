package com.nfv.validator.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a single field ignore rule
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IgnoreRule {
    /**
     * Resource type to apply this rule to (e.g., "Deployment", "Service")
     * Empty string means apply to all resource types
     */
    private String resourceType;
    
    /**
     * JSON path to the field to ignore (e.g., "metadata.creationTimestamp", "spec.replicas")
     */
    private String jsonPath;
    
    /**
     * Check if this rule applies to a given resource type
     */
    public boolean appliesTo(String kind) {
        return resourceType == null || resourceType.isEmpty() || resourceType.equals(kind);
    }
    
    /**
     * Check if a field path matches this rule
     */
    public boolean matches(String fieldPath) {
        if (jsonPath == null) {
            return false;
        }
        
        // Exact match
        if (fieldPath.equals(jsonPath)) {
            return true;
        }
        
        // Wildcard match (e.g., "metadata.*" matches "metadata.creationTimestamp")
        if (jsonPath.endsWith(".*")) {
            String prefix = jsonPath.substring(0, jsonPath.length() - 2);
            return fieldPath.startsWith(prefix + ".");
        }
        
        // Prefix match for nested fields
        if (fieldPath.startsWith(jsonPath + ".")) {
            return true;
        }
        
        return false;
    }
}
