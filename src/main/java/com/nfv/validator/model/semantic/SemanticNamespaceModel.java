package com.nfv.validator.model.semantic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Semantic representation of a Kubernetes namespace
 * Contains objects in structured format for semantic comparison
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticNamespaceModel {
    
    private String name;
    private String clusterName;
    
    // Map: objectName -> SemanticObjectModel
    private Map<String, SemanticObjectModel> objects = new HashMap<>();
    
    /**
     * Add an object to the namespace
     */
    public void addObject(String name, SemanticObjectModel object) {
        if (objects == null) {
            objects = new HashMap<>();
        }
        objects.put(name, object);
    }
    
    /**
     * Get an object by name
     */
    public SemanticObjectModel getObject(String name) {
        return objects != null ? objects.get(name) : null;
    }
    
    /**
     * Get all objects of a specific kind
     */
    public Map<String, SemanticObjectModel> getObjectsByKind(String kind) {
        Map<String, SemanticObjectModel> result = new HashMap<>();
        if (objects != null) {
            for (Map.Entry<String, SemanticObjectModel> entry : objects.entrySet()) {
                if (kind.equals(entry.getValue().getKind())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
    
    /**
     * Get object count
     */
    public int getObjectCount() {
        return objects != null ? objects.size() : 0;
    }
}
