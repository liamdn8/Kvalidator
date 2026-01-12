package com.nfv.validator.comparison;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.comparison.*;
import com.nfv.validator.model.FlatObjectModel;

import java.util.*;

/**
 * Comparator for namespace-level comparisons
 */
public class NamespaceComparator {

    /**
     * Compare two namespaces (represented as maps of FlatObjectModel)
     */
    public static NamespaceComparison compareNamespace(
            Map<String, FlatObjectModel> left,
            Map<String, FlatObjectModel> right,
            String leftName,
            String rightName) {
        return compareNamespace(left, right, leftName, rightName, null);
    }
    
    /**
     * Compare two namespaces with validation config for field filtering
     */
    public static NamespaceComparison compareNamespace(
            Map<String, FlatObjectModel> left,
            Map<String, FlatObjectModel> right,
            String leftName,
            String rightName,
            ValidationConfig config) {
        
        NamespaceComparison comparison = new NamespaceComparison();
        comparison.setLeftNamespace(leftName);
        comparison.setRightNamespace(rightName);
        
        // Get all unique object IDs from both sides
        Set<String> allObjectIds = new HashSet<>();
        allObjectIds.addAll(left.keySet());
        allObjectIds.addAll(right.keySet());
        
        // Compare each object
        for (String objectId : allObjectIds) {
            FlatObjectModel leftObj = left.get(objectId);
            FlatObjectModel rightObj = right.get(objectId);
            
            ObjectComparison objComparison = compareObjects(objectId, leftObj, rightObj, config);
            comparison.addObjectResult(objectId, objComparison);
        }
        
        return comparison;
    }
    
    /**
     * Compare two FlatObjectModel instances
     */
    private static ObjectComparison compareObjects(String objectId, 
                                                   FlatObjectModel left, 
                                                   FlatObjectModel right,
                                                   ValidationConfig config) {
        ObjectComparison comparison = new ObjectComparison();
        comparison.setObjectId(objectId);
        
        // Determine object type
        String type = null;
        if (left != null) {
            type = left.getKind();
        } else if (right != null) {
            type = right.getKind();
        }
        comparison.setObjectType(type);
        
        // Handle cases where object exists only on one side
        if (left == null) {
            // Object only in right
            KeyComparison item = new KeyComparison();
            item.setKey(objectId);
            item.setLeftValue(null);
            item.setRightValue("exists");
            item.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
            comparison.addItem(item);
            return comparison;
        }
        
        if (right == null) {
            // Object only in left
            KeyComparison item = new KeyComparison();
            item.setKey(objectId);
            item.setLeftValue("exists");
            item.setRightValue(null);
            item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            comparison.addItem(item);
            return comparison;
        }
        
        // Both objects exist - compare all fields (filtered if config is provided)
        Map<String, String> leftFields = config != null ? left.getAllFieldsFiltered(config) : left.getAllFields();
        Map<String, String> rightFields = config != null ? right.getAllFieldsFiltered(config) : right.getAllFields();
        
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(leftFields.keySet());
        allKeys.addAll(rightFields.keySet());
        
        for (String key : allKeys) {
            String leftValue = leftFields.get(key);
            String rightValue = rightFields.get(key);
            
            KeyComparison item = new KeyComparison();
            item.setKey(key);
            item.setLeftValue(leftValue);
            item.setRightValue(rightValue);
            item.setStatus(determineStatus(leftValue, rightValue));
            
            comparison.addItem(item);
        }
        
        return comparison;
    }
    
    /**
     * Determine comparison status for two values
     */
    private static ComparisonStatus determineStatus(String left, String right) {
        if (left == null && right == null) {
            return ComparisonStatus.BOTH_NULL;
        }
        if (left == null) {
            return ComparisonStatus.ONLY_IN_RIGHT;
        }
        if (right == null) {
            return ComparisonStatus.ONLY_IN_LEFT;
        }
        if (left.equals(right)) {
            return ComparisonStatus.MATCH;
        }
        return ComparisonStatus.DIFFERENT;
    }
}
