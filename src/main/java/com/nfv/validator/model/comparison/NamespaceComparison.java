package com.nfv.validator.model.comparison;

import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents comparison result for an entire namespace
 */
@Data
public class NamespaceComparison {
    /**
     * Left/source namespace name
     */
    private String leftNamespace;
    
    /**
     * Right/target namespace name
     */
    private String rightNamespace;
    
    /**
     * Map of object comparisons by object ID
     */
    private Map<String, ObjectComparison> objectComparisons = new HashMap<>();
    
    /**
     * Add an object comparison result
     */
    public void addObjectResult(String objectId, ObjectComparison comparison) {
        objectComparisons.put(objectId, comparison);
    }
    
    /**
     * Get objects grouped by type
     */
    public Map<String, List<ObjectComparison>> getObjectsByType() {
        return objectComparisons.values().stream()
                .collect(Collectors.groupingBy(ObjectComparison::getObjectType));
    }
    
    /**
     * Get only objects with differences
     */
    public List<ObjectComparison> getObjectsWithDifferences() {
        return objectComparisons.values().stream()
                .filter(obj -> obj.getDifferenceCount() > 0)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all object results as a list
     */
    public List<ObjectComparison> getAllObjectResults() {
        return new ArrayList<>(objectComparisons.values());
    }
    
    /**
     * Get total number of differences across all objects
     */
    public int getDifferenceCount() {
        return objectComparisons.values().stream()
                .mapToInt(ObjectComparison::getDifferenceCount)
                .sum();
    }
    
    /**
     * Get summary statistics
     */
    public ComparisonSummary getSummary() {
        ComparisonSummary summary = new ComparisonSummary();
        
        long onlyInLeft = objectComparisons.values().stream()
                .filter(obj -> obj.getItems().stream()
                        .anyMatch(item -> item.getStatus() == ComparisonStatus.ONLY_IN_LEFT))
                .count();
        
        long onlyInRight = objectComparisons.values().stream()
                .filter(obj -> obj.getItems().stream()
                        .anyMatch(item -> item.getStatus() == ComparisonStatus.ONLY_IN_RIGHT))
                .count();
        
        long matched = objectComparisons.values().stream()
                .filter(ObjectComparison::isFullMatch)
                .count();
        
        long withDifferences = objectComparisons.values().stream()
                .filter(obj -> obj.getDifferenceCount() > 0)
                .count();
        
        summary.setTotalInLeft((int) (objectComparisons.size() - onlyInRight));
        summary.setTotalInRight((int) (objectComparisons.size() - onlyInLeft));
        summary.setOnlyInLeft((int) onlyInLeft);
        summary.setOnlyInRight((int) onlyInRight);
        summary.setCommonObjects(objectComparisons.size());
        summary.setMatchedObjects((int) matched);
        summary.setDifferencesCount((int) withDifferences);
        
        if (objectComparisons.size() > 0) {
            summary.setMatchPercentage((matched * 100.0) / objectComparisons.size());
        }
        
        return summary;
    }
    
    /**
     * Summary statistics for namespace comparison
     */
    @Data
    public static class ComparisonSummary {
        private int totalInLeft;
        private int totalInRight;
        private int onlyInLeft;
        private int onlyInRight;
        private int commonObjects;
        private int matchedObjects;
        private int differencesCount;
        private double matchPercentage;
    }
}
