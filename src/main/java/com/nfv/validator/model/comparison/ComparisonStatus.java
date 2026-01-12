package com.nfv.validator.model.comparison;

/**
 * Status enum for comparison results
 */
public enum ComparisonStatus {
    /**
     * Values match exactly
     */
    MATCH,
    
    /**
     * Values differ
     */
    DIFFERENT,
    
    /**
     * Value exists only in left/source
     */
    ONLY_IN_LEFT,
    
    /**
     * Value exists only in right/target
     */
    ONLY_IN_RIGHT,
    
    /**
     * Value is null in both sides
     */
    BOTH_NULL
}
