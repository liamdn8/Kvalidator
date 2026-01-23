package com.nfv.validator.comparison;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.comparison.*;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * V2 Comparator - Semantic comparison engine
 * Compares structured objects without relying on ordering
 * Uses identity-based matching for list items
 */
@Slf4j
public class NamespaceComparatorV2 {

    /**
     * Compare two namespaces using semantic comparison
     */
    public static NamespaceComparison compareNamespace(
            SemanticNamespaceModel left,
            SemanticNamespaceModel right,
            String leftName,
            String rightName,
            ValidationConfig config) {
        
        log.info("[V2] Comparing namespaces: '{}' vs '{}'", leftName, rightName);
        
        NamespaceComparison comparison = new NamespaceComparison();
        comparison.setLeftNamespace(leftName);
        comparison.setRightNamespace(rightName);
        
        // Get all unique object IDs
        Set<String> allObjectIds = new HashSet<>();
        if (left != null && left.getObjects() != null) {
            allObjectIds.addAll(left.getObjects().keySet());
        }
        if (right != null && right.getObjects() != null) {
            allObjectIds.addAll(right.getObjects().keySet());
        }
        
        // Compare each object
        for (String objectId : allObjectIds) {
            SemanticObjectModel leftObj = left != null ? left.getObject(objectId) : null;
            SemanticObjectModel rightObj = right != null ? right.getObject(objectId) : null;
            
            ObjectComparison objComparison = compareObjects(objectId, leftObj, rightObj, config);
            comparison.addObjectResult(objectId, objComparison);
        }
        
        log.info("[V2] Comparison complete: {} objects compared", allObjectIds.size());
        
        return comparison;
    }

    /**
     * Compare two SemanticObjectModel instances
     */
    private static ObjectComparison compareObjects(
            String objectId,
            SemanticObjectModel left,
            SemanticObjectModel right,
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
        
        // Handle missing objects
        if (left == null) {
            KeyComparison item = new KeyComparison();
            item.setKey(objectId);
            item.setLeftValue(null);
            item.setRightValue("exists");
            item.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
            comparison.addItem(item);
            return comparison;
        }
        
        if (right == null) {
            KeyComparison item = new KeyComparison();
            item.setKey(objectId);
            item.setLeftValue("exists");
            item.setRightValue(null);
            item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            comparison.addItem(item);
            return comparison;
        }
        
        // Both exist - perform semantic deep comparison
        compareNestedStructure("metadata", left.getMetadata(), right.getMetadata(), comparison, config);
        compareNestedStructure("spec", left.getSpec(), right.getSpec(), comparison, config);
        compareNestedStructure("data", left.getData(), right.getData(), comparison, config);
        
        return comparison;
    }

    /**
     * Recursively compare nested structures (maps, lists, primitives)
     * This is the CORE of semantic comparison
     */
    @SuppressWarnings("unchecked")
    private static void compareNestedStructure(
            String path,
            Object leftValue,
            Object rightValue,
            ObjectComparison comparison,
            ValidationConfig config) {
        
        // Skip if configured to ignore
        if (config != null && config.shouldIgnore(path)) {
            log.debug("[V2] Skipping ignored field: {}", path);
            return;
        }
        
        // Both null
        if (leftValue == null && rightValue == null) {
            return;
        }
        
        // One is null
        if (leftValue == null) {
            KeyComparison item = createComparison(path, null, String.valueOf(rightValue));
            item.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
            comparison.addItem(item);
            return;
        }
        
        if (rightValue == null) {
            KeyComparison item = createComparison(path, String.valueOf(leftValue), null);
            item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            comparison.addItem(item);
            return;
        }
        
        // Both exist - compare by type
        if (leftValue instanceof Map && rightValue instanceof Map) {
            compareMap(path, (Map<String, Object>) leftValue, (Map<String, Object>) rightValue, comparison, config);
        } else if (leftValue instanceof List && rightValue instanceof List) {
            compareList(path, (List<Object>) leftValue, (List<Object>) rightValue, comparison, config);
        } else {
            // Primitive comparison
            String leftStr = String.valueOf(leftValue);
            String rightStr = String.valueOf(rightValue);
            
            if (!leftStr.equals(rightStr)) {
                KeyComparison item = createComparison(path, leftStr, rightStr);
                item.setStatus(ComparisonStatus.DIFFERENT);
                comparison.addItem(item);
            } else {
                // Match - optionally add to comparison
                KeyComparison item = createComparison(path, leftStr, rightStr);
                item.setStatus(ComparisonStatus.MATCH);
                comparison.addItem(item);
            }
        }
    }

    /**
     * Compare two maps recursively
     */
    private static void compareMap(
            String path,
            Map<String, Object> leftMap,
            Map<String, Object> rightMap,
            ObjectComparison comparison,
            ValidationConfig config) {
        
        // Get all keys
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(leftMap.keySet());
        allKeys.addAll(rightMap.keySet());
        
        // Compare each key
        for (String key : allKeys) {
            String newPath = path.isEmpty() ? key : path + "." + key;
            Object leftVal = leftMap.get(key);
            Object rightVal = rightMap.get(key);
            
            compareNestedStructure(newPath, leftVal, rightVal, comparison, config);
        }
    }

    /**
     * Compare two lists using SEMANTIC MATCHING (identity-based)
     * This is the KEY INNOVATION of V2!
     */
    @SuppressWarnings("unchecked")
    private static void compareList(
            String path,
            List<Object> leftList,
            List<Object> rightList,
            ObjectComparison comparison,
            ValidationConfig config) {
        
        // Check if this is a structured list (list of objects)
        if (!leftList.isEmpty() && leftList.get(0) instanceof Map) {
            // Semantic matching - match by identity
            compareStructuredList(path, 
                    (List<Map<String, Object>>) (List<?>) leftList,
                    (List<Map<String, Object>>) (List<?>) rightList,
                    comparison, config);
        } else {
            // Simple list - compare as set (order-independent)
            compareSimpleList(path, leftList, rightList, comparison);
        }
    }

    /**
     * Compare structured lists (e.g., containers, volumes) by identity
     * ORDER-INDEPENDENT comparison!
     */
    private static void compareStructuredList(
            String path,
            List<Map<String, Object>> leftList,
            List<Map<String, Object>> rightList,
            ObjectComparison comparison,
            ValidationConfig config) {
        
        log.debug("[V2] Comparing structured list: {} (left: {}, right: {})", 
                path, leftList.size(), rightList.size());
        
        // Build identity maps
        Map<String, Map<String, Object>> leftByIdentity = new HashMap<>();
        Map<String, Map<String, Object>> rightByIdentity = new HashMap<>();
        
        for (Map<String, Object> item : leftList) {
            String identity = SemanticObjectModel.getIdentityValue(item);
            leftByIdentity.put(identity, item);
        }
        
        for (Map<String, Object> item : rightList) {
            String identity = SemanticObjectModel.getIdentityValue(item);
            rightByIdentity.put(identity, item);
        }
        
        // Get all identities
        Set<String> allIdentities = new HashSet<>();
        allIdentities.addAll(leftByIdentity.keySet());
        allIdentities.addAll(rightByIdentity.keySet());
        
        // Compare matched items
        for (String identity : allIdentities) {
            Map<String, Object> leftItem = leftByIdentity.get(identity);
            Map<String, Object> rightItem = rightByIdentity.get(identity);
            
            String itemPath = path + "[" + identity + "]";
            
            if (leftItem == null) {
                // Only in right
                KeyComparison item = createComparison(itemPath, null, "exists");
                item.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
                comparison.addItem(item);
            } else if (rightItem == null) {
                // Only in left
                KeyComparison item = createComparison(itemPath, "exists", null);
                item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
                comparison.addItem(item);
            } else {
                // Both exist - compare deeply
                compareMap(itemPath, leftItem, rightItem, comparison, config);
            }
        }
    }

    /**
     * Compare simple lists (primitives) as sets - order doesn't matter
     */
    private static void compareSimpleList(
            String path,
            List<Object> leftList,
            List<Object> rightList,
            ObjectComparison comparison) {
        
        // Convert to sets for order-independent comparison
        Set<String> leftSet = new HashSet<>();
        Set<String> rightSet = new HashSet<>();
        
        for (Object obj : leftList) {
            leftSet.add(String.valueOf(obj));
        }
        
        for (Object obj : rightList) {
            rightSet.add(String.valueOf(obj));
        }
        
        // Find differences
        Set<String> onlyInLeft = new HashSet<>(leftSet);
        onlyInLeft.removeAll(rightSet);
        
        Set<String> onlyInRight = new HashSet<>(rightSet);
        onlyInRight.removeAll(leftSet);
        
        // Report differences
        for (String value : onlyInLeft) {
            KeyComparison item = createComparison(path, value, null);
            item.setStatus(ComparisonStatus.ONLY_IN_LEFT);
            comparison.addItem(item);
        }
        
        for (String value : onlyInRight) {
            KeyComparison item = createComparison(path, null, value);
            item.setStatus(ComparisonStatus.ONLY_IN_RIGHT);
            comparison.addItem(item);
        }
        
        // If no differences and lists not empty, mark as match
        if (onlyInLeft.isEmpty() && onlyInRight.isEmpty() && !leftSet.isEmpty()) {
            KeyComparison item = createComparison(path, 
                    "list[" + leftSet.size() + " items]",
                    "list[" + rightSet.size() + " items]");
            item.setStatus(ComparisonStatus.MATCH);
            comparison.addItem(item);
        }
    }

    /**
     * Helper to create KeyComparison
     */
    private static KeyComparison createComparison(String key, String leftValue, String rightValue) {
        KeyComparison item = new KeyComparison();
        item.setKey(key);
        item.setLeftValue(leftValue);
        item.setRightValue(rightValue);
        return item;
    }
}
