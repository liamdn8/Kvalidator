package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single CNF checklist validation item
 * Represents one field to validate in a Kubernetes object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CNFChecklistItem {
    
    /**
     * VIM name / Cluster site identifier (e.g., "vim-hanoi", "vim-hcm")
     */
    @JsonProperty("vimName")
    private String vimName;
    
    /**
     * Namespace where the Kubernetes object is located
     */
    @JsonProperty("namespace")
    private String namespace;
    
    /**
     * Kubernetes resource kind (e.g., Deployment, ConfigMap, Service)
     */
    @JsonProperty("kind")
    private String kind;
    
    /**
     * Name of the Kubernetes object
     */
    @JsonProperty("objectName")
    private String objectName;
    
    /**
     * Field key to validate (e.g., "spec.template.spec.containers[0].image")
     */
    @JsonProperty("fieldKey")
    private String fieldKey;
    
    /**
     * Expected value from MANO (design/baseline)
     */
    @JsonProperty("manoValue")
    private String manoValue;
    
    /**
     * Validate this item has all required fields
     */
    public void validate() throws IllegalArgumentException {
        if (vimName == null || vimName.trim().isEmpty()) {
            throw new IllegalArgumentException("VIM name is required");
        }
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("Namespace is required");
        }
        if (kind == null || kind.trim().isEmpty()) {
            throw new IllegalArgumentException("Kind is required");
        }
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new IllegalArgumentException("Object name is required");
        }
        if (fieldKey == null || fieldKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Field key is required");
        }
        if (manoValue == null || manoValue.trim().isEmpty()) {
            throw new IllegalArgumentException("MANO value is required");
        }
    }
    
    /**
     * Get unique identifier for this checklist item
     * Format: vimName/namespace/kind/objectName/fieldKey
     */
    public String getUniqueId() {
        return String.format("%s/%s/%s/%s/%s", vimName, namespace, kind, objectName, fieldKey);
    }
}
