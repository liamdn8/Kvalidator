package com.nfv.validator.model;

import com.nfv.validator.config.ValidationConfig;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flattened representation of a Kubernetes object
 * Supports all object types: Deployment, StatefulSet, DaemonSet, Service, ConfigMap, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlatObjectModel {
    // Basic Kubernetes object fields
    private String kind;
    private String apiVersion;
    private String name;
    private String namespace;

    // Metadata represented as key-value pairs (flattened)
    // e.g., "labels.app": "nginx", "annotations.version": "1.0"
    private Map<String, String> metadata;

    // Spec represented as key-value pairs (flattened)
    // e.g., "replicas": "3", "template.spec.containers[0].image": "nginx:1.19"
    private Map<String, String> spec;
    
    // Data represented as key-value pairs (flattened) - for ConfigMap, Secret, etc.
    // e.g., "application.yaml": "...", "config.json": "..."
    private Map<String, String> data;
    
    /**
     * Add a metadata entry
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Add a spec entry
     */
    public void addSpec(String key, String value) {
        if (spec == null) {
            spec = new HashMap<>();
        }
        spec.put(key, value);
    }
    
    /**
     * Add a data entry (for ConfigMap, Secret, etc.)
     */
    public void addData(String key, String value) {
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key, value);
    }
    
    /**
     * Get all flattened fields (metadata + spec + data combined)
     */
    public Map<String, String> getAllFields() {
        Map<String, String> allFields = new HashMap<>();
        
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                allFields.put("metadata." + entry.getKey(), entry.getValue());
            }
        }
        
        if (spec != null) {
            for (Map.Entry<String, String> entry : spec.entrySet()) {
                allFields.put("spec." + entry.getKey(), entry.getValue());
            }
        }
                if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                allFields.put("data." + entry.getKey(), entry.getValue());
            }
        }
                return allFields;
    }
    
    /**
     * Get all fields, excluding ignored fields based on validation config
     */
    public Map<String, String> getAllFieldsFiltered(ValidationConfig config) {
        if (config == null) {
            return getAllFields();
        }
        
        Map<String, String> allFields = getAllFields();
        Map<String, String> filtered = new HashMap<>();
        
        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            String fieldPath = entry.getKey();
            if (!config.shouldIgnore(fieldPath)) {
                filtered.put(fieldPath, entry.getValue());
            }
        }
        
        return filtered;
    }
}

