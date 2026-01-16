package com.nfv.validator.model.semantic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Semantic representation of a Kubernetes object
 * Preserves nested structure for accurate semantic comparison
 * Unlike FlatObjectModel, this keeps list objects (containers, volumes, etc.) as structured lists
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticObjectModel {
    
    // Basic Kubernetes object fields
    private String kind;
    private String apiVersion;
    private String name;
    private String namespace;
    
    // Metadata as nested structure
    private Map<String, Object> metadata;
    
    // Spec as nested structure (preserves lists and nested objects)
    private Map<String, Object> spec;
    
    /**
     * Get a nested value by path (e.g., "template.spec.containers")
     */
    @SuppressWarnings("unchecked")
    public Object getNestedValue(String path) {
        String[] parts = path.split("\\.");
        Object current = null;
        
        // Start from metadata or spec
        if (parts[0].equals("metadata") && metadata != null) {
            current = metadata;
        } else if (parts[0].equals("spec") && spec != null) {
            current = spec;
        } else {
            return null;
        }
        
        // Navigate through path
        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(parts[i]);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Get list of containers (common use case)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getContainers() {
        Object containers = getNestedValue("spec.template.spec.containers");
        if (containers instanceof List) {
            return (List<Map<String, Object>>) containers;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get list of volumes (common use case)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getVolumes() {
        Object volumes = getNestedValue("spec.template.spec.volumes");
        if (volumes instanceof List) {
            return (List<Map<String, Object>>) volumes;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get environment variables for a specific container
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getEnvVars(String containerName) {
        List<Map<String, Object>> containers = getContainers();
        for (Map<String, Object> container : containers) {
            if (containerName.equals(container.get("name"))) {
                Object env = container.get("env");
                if (env instanceof List) {
                    return (List<Map<String, Object>>) env;
                }
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * Extract identity field from a map (name, containerPort, etc.)
     */
    public static String getIdentityValue(Map<String, Object> obj) {
        // Priority order for identity fields
        if (obj.containsKey("name")) {
            return String.valueOf(obj.get("name"));
        }
        if (obj.containsKey("containerPort")) {
            return String.valueOf(obj.get("containerPort"));
        }
        if (obj.containsKey("port")) {
            return String.valueOf(obj.get("port"));
        }
        if (obj.containsKey("ip")) {
            return String.valueOf(obj.get("ip"));
        }
        if (obj.containsKey("key")) {
            return String.valueOf(obj.get("key"));
        }
        
        // Fallback: compute hash of all fields
        return "hash_" + Objects.hash(obj);
    }
    
    /**
     * Check if this is a structured list (list of objects with identities)
     */
    public static boolean isStructuredList(Object value) {
        if (!(value instanceof List)) {
            return false;
        }
        
        List<?> list = (List<?>) value;
        if (list.isEmpty()) {
            return false;
        }
        
        // Check if first item is a Map (object)
        return list.get(0) instanceof Map;
    }
}
