package com.nfv.validator.model.comparison;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single field/key comparison result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyComparison {
    /**
     * The key/field name being compared
     */
    private String key;
    
    /**
     * Value from the left/source object
     */
    private String leftValue;
    
    /**
     * Value from the right/target object
     */
    private String rightValue;
    
    /**
     * Comparison status
     */
    private ComparisonStatus status;
    
    /**
     * Check if values match
     */
    public boolean isMatch() {
        return status == ComparisonStatus.MATCH || status == ComparisonStatus.BOTH_NULL;
    }
    
    /**
     * Check if there's a difference
     */
    public boolean hasDifference() {
        return status == ComparisonStatus.DIFFERENT || 
               status == ComparisonStatus.ONLY_IN_LEFT || 
               status == ComparisonStatus.ONLY_IN_RIGHT;
    }
}
