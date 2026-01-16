package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * CNF Checklist validation request
 * Contains list of items to validate against Kubernetes cluster
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CNFChecklistRequest {
    
    /**
     * List of checklist items to validate
     */
    @JsonProperty("items")
    @Builder.Default
    private List<CNFChecklistItem> items = new ArrayList<>();
    
    /**
     * Optional description of this checklist validation
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * Optional cluster name (default: current context)
     */
    @JsonProperty("cluster")
    private String cluster;
    
    /**
     * Validate the entire checklist request
     */
    public void validate() throws IllegalArgumentException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one checklist item is required");
        }
        
        // Validate each item
        for (int i = 0; i < items.size(); i++) {
            CNFChecklistItem item = items.get(i);
            try {
                item.validate();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Validation failed for item #" + (i + 1) + ": " + e.getMessage()
                );
            }
        }
    }
}
