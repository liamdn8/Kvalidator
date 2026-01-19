package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
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
@NoArgsConstructor
@AllArgsConstructor
public class CNFChecklistRequest {
    
    /**
     * List of checklist items to validate
     */
    @JsonProperty("items")
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
     * Global matching strategy for field comparison
     * - "exact": Exact field path match (V1 engine, fastest)
     * - "value": Search by value in list (V1 flexible) - DEFAULT
     * - "identity": Use semantic/identity-based matching (V2 engine)
     */
    @JsonProperty("matchingStrategy")
    private String matchingStrategy = "value";
    
    /**
     * Custom ignore fields for this validation (optional)
     * These will be merged with default ignore rules from config
     */
    @JsonProperty("ignoreFields")
    private List<String> ignoreFields;
    
    /**
     * Determine if V2 semantic comparison should be used based on matching strategy
     * @return true if matchingStrategy is "identity", false otherwise
     */
    public boolean shouldUseSemanticV2() {
        return "identity".equalsIgnoreCase(matchingStrategy);
    }
    
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
