package com.nfv.validator.model;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flattened representation of a Kubernetes namespace
 * Contains all Kubernetes objects within the namespace in flattened format
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlatNamespaceModel {
    // Kubernetes Namespace basic fields
    private String name;
    
    // Cluster identifier (for multi-cluster scenarios)
    private String clusterName;

    // Map of object name to FlatObjectModel
    // Key: object name (e.g., "nginx-deployment")
    // Value: FlatObjectModel containing flattened object data
    private Map<String, FlatObjectModel> objects;
    
    /**
     * Add an object to the namespace model
     */
    public void addObject(String name, FlatObjectModel object) {
        if (objects == null) {
            objects = new HashMap<>();
        }
        objects.put(name, object);
    }
    
    /**
     * Get object by name
     */
    public FlatObjectModel getObject(String name) {
        return objects != null ? objects.get(name) : null;
    }
    
    /**
     * Get all objects of a specific kind
     */
    public Map<String, FlatObjectModel> getObjectsByKind(String kind) {
        Map<String, FlatObjectModel> result = new HashMap<>();
        if (objects != null) {
            for (Map.Entry<String, FlatObjectModel> entry : objects.entrySet()) {
                if (kind.equals(entry.getValue().getKind())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
}
