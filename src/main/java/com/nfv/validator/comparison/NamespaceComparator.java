package com.nfv.validator.comparison;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.comparison.*;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.FlatNamespaceModel;

import java.util.*;
import java.util.Objects;

/**
 * Comparator for namespace-level comparisons
 */
public class NamespaceComparator {

    /**
     * Compare namespaces using flattened baseline (CNF mode)
     * Only compares fields that exist in the baseline
     */
    public static NamespaceComparison compareWithFlattenedBaseline(
            Map<String, FlatNamespaceModel> flattenedBaseline,
            Map<String, FlatObjectModel> actual,
            String actualNamespaceName,
            ValidationConfig config) {
        
        // Get the baseline namespace model
        String baselineKey = flattenedBaseline.keySet().iterator().next(); // Should only have one
        FlatNamespaceModel baselineNamespace = flattenedBaseline.get(baselineKey);
        String baselineLabel = baselineKey + " (Baseline)";
        String actualLabel = actualNamespaceName + " (Actual)";
        
        NamespaceComparison comparison = new NamespaceComparison();
        comparison.setLeftNamespace(baselineLabel);
        comparison.setRightNamespace(actualLabel);
        
        // Only compare objects that exist in baseline
        for (Map.Entry<String, FlatObjectModel> baselineEntry : baselineNamespace.getObjects().entrySet()) {
            String objectId = baselineEntry.getKey();
            FlatObjectModel baselineObj = baselineEntry.getValue();
            FlatObjectModel actualObj = actual.get(objectId);
            
            // Create filtered comparison - only compare fields from baseline
            ObjectComparison objComparison = compareObjectsFiltered(objectId, baselineObj, actualObj, config);
            comparison.addObjectResult(objectId, objComparison);
        }
        
        return comparison;
    }

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
     * Compare objects with field filtering (only fields from baseline)
     * Used for CNF checklist validation where we only want to compare specific fields
     */
    private static ObjectComparison compareObjectsFiltered(String objectId, 
                                                          FlatObjectModel baseline, 
                                                          FlatObjectModel actual,
                                                          ValidationConfig config) {
        ObjectComparison comparison = new ObjectComparison();
        comparison.setObjectId(objectId);
        comparison.setObjectType(baseline != null ? baseline.getKind() : "Unknown");
        
        // Handle case where actual object doesn't exist
        if (actual == null) {
            // Object missing in actual - create comparison items for all baseline fields
            Map<String, String> baselineFields = config != null ? baseline.getAllFieldsFiltered(config) : baseline.getAllFields();
            for (Map.Entry<String, String> entry : baselineFields.entrySet()) {
                KeyComparison item = new KeyComparison();
                item.setKey(entry.getKey());
                item.setLeftValue(entry.getValue());
                item.setRightValue(null);
                item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
                comparison.addItem(item);
            }
            return comparison;
        }
        
        // Both objects exist - compare only fields that exist in baseline
        Map<String, String> baselineFields = config != null ? baseline.getAllFieldsFiltered(config) : baseline.getAllFields();
        Map<String, String> actualFields = config != null ? actual.getAllFieldsFiltered(config) : actual.getAllFields();
        
        // Only compare fields from baseline
        for (Map.Entry<String, String> baselineEntry : baselineFields.entrySet()) {
            String key = baselineEntry.getKey();
            String baselineValue = baselineEntry.getValue();
            String actualValue = actualFields.get(key);
            
            KeyComparison item = new KeyComparison();
            item.setKey(key);
            item.setLeftValue(baselineValue);
            item.setRightValue(actualValue);
            
            // Determine comparison status
            if (actualValue == null) {
                item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            } else if (Objects.equals(baselineValue, actualValue)) {
                item.setStatus(ComparisonStatus.MATCH);
            } else {
                item.setStatus(ComparisonStatus.DIFFERENT);
            }
            
            comparison.addItem(item);
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
        
        // Only compare keys that exist in left (baseline)
        // This allows validating only specified fields in CNF checklist scenarios
        Set<String> keysToCompare = new HashSet<>(leftFields.keySet());
        
        for (String key : keysToCompare) {
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
