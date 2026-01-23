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
        
        // Track which baseline fields have been processed
        Set<String> processedBaselineKeys = new HashSet<>();
        
        // Only compare fields from baseline
        for (Map.Entry<String, String> baselineEntry : baselineFields.entrySet()) {
            String key = baselineEntry.getKey();
            String baselineValue = baselineEntry.getValue();
            String actualValue = actualFields.get(key);
            
            KeyComparison item = new KeyComparison();
            item.setKey(key);
            item.setLeftValue(baselineValue);
            item.setRightValue(actualValue);
            
            // Normalize values for comparison (handles ConfigMap data trailing newlines)
            String normalizedBaseline = normalizeValue(key, baselineValue);
            String normalizedActual = normalizeValue(key, actualValue);
            
            // Determine comparison status
            if (actualValue == null) {
                item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            } else if (Objects.equals(normalizedBaseline, normalizedActual)) {
                item.setStatus(ComparisonStatus.MATCH);
            } else {
                item.setStatus(ComparisonStatus.DIFFERENT);
            }
            
            comparison.addItem(item);
            processedBaselineKeys.add(key);
            
            // CRITICAL FIX for Value Search: Include ALL runtime list items for flexible matching
            // If this is a list field (contains [index]), add all other indices from runtime
            // Example: baseline has env[0].name, runtime has env[0], env[1], env[2]
            // We need to add env[1].name, env[2].name to comparison so Value Search can scan them
            if (key.contains("[") && key.contains("]")) {
                // Extract list pattern: "spec.template.spec.containers[0].image" -> "spec.template.spec.containers[*]"
                int bracketStart = key.lastIndexOf("[");
                int bracketEnd = key.indexOf("]", bracketStart);
                
                if (bracketStart > 0 && bracketEnd > bracketStart) {
                    String prefix = key.substring(0, bracketStart + 1);  // e.g., "spec.containers["
                    String suffix = key.substring(bracketEnd);           // e.g., "].name"
                    
                    // Find all runtime keys matching this pattern
                    for (Map.Entry<String, String> actualEntry : actualFields.entrySet()) {
                        String actualKey = actualEntry.getKey();
                        
                        // Check if this key matches the pattern but with different index
                        if (actualKey.startsWith(prefix) && actualKey.endsWith(suffix) && !processedBaselineKeys.contains(actualKey)) {
                            // Add this runtime item to comparison (with null baseline value)
                            // This allows Value Search to scan ALL runtime items, not just baseline index
                            KeyComparison extraItem = new KeyComparison();
                            extraItem.setKey(actualKey);
                            extraItem.setLeftValue(null);  // Not in baseline
                            extraItem.setRightValue(actualEntry.getValue());
                            extraItem.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
                            
                            comparison.addItem(extraItem);
                            processedBaselineKeys.add(actualKey);
                        }
                    }
                }
            }
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
     * Normalize value for comparison (strip trailing newlines for ConfigMap data)
     * Kubernetes ConfigMaps automatically add trailing newlines to data values
     */
    private static String normalizeValue(String fieldKey, String value) {
        if (value == null) {
            return null;
        }
        // For ConfigMap data fields, strip trailing newlines
        if (fieldKey != null && fieldKey.startsWith("data.")) {
            return value.replaceAll("\\n+$", "");
        }
        return value;
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
