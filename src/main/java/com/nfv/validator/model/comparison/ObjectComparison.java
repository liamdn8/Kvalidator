package com.nfv.validator.model.comparison;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents comparison result for a single object (containing multiple field comparisons)
 */
@Data
public class ObjectComparison {
    /**
     * Object identifier (e.g., resource name)
     */
    private String objectId;
    
    /**
     * Object type (e.g., Deployment, Service)
     */
    private String objectType;
    
    /**
     * List of field-level comparison items
     */
    private List<KeyComparison> items = new ArrayList<>();
    
    /**
     * Add a comparison item
     */
    public void addItem(KeyComparison item) {
        items.add(item);
    }
    
    /**
     * Get only items with differences
     */
    public List<KeyComparison> getDifferences() {
        return items.stream()
                .filter(KeyComparison::hasDifference)
                .collect(Collectors.toList());
    }
    
    /**
     * Get only matching items
     */
    public List<KeyComparison> getMatches() {
        return items.stream()
                .filter(KeyComparison::isMatch)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if all fields match
     */
    public boolean isFullMatch() {
        return items.stream().allMatch(KeyComparison::isMatch);
    }
    
    /**
     * Get count of differences
     */
    public int getDifferenceCount() {
        return (int) items.stream().filter(KeyComparison::hasDifference).count();
    }
    
    /**
     * Get count of matches
     */
    public int getMatchCount() {
        return (int) items.stream().filter(KeyComparison::isMatch).count();
    }
    
    /**
     * Get match percentage
     */
    public double getMatchPercentage() {
        if (items.isEmpty()) return 100.0;
        return (getMatchCount() * 100.0) / items.size();
    }
}
