package com.nfv.validator.adapter;

import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Adapter to convert between Semantic and Flat models
 * Enables backward compatibility with existing APIs and reports
 */
@Slf4j
public class SemanticToFlatAdapter {

    /**
     * Convert SemanticNamespaceModel to FlatNamespaceModel
     * Used when API expects flat format
     */
    public static FlatNamespaceModel toFlatModel(SemanticNamespaceModel semantic) {
        if (semantic == null) {
            return null;
        }
        
        log.debug("[Adapter] Converting semantic to flat model: {}", semantic.getName());
        
        FlatNamespaceModel flat = new FlatNamespaceModel();
        flat.setName(semantic.getName());
        flat.setClusterName(semantic.getClusterName());
        flat.setObjects(new HashMap<>());
        
        // Convert each object
        if (semantic.getObjects() != null) {
            for (Map.Entry<String, SemanticObjectModel> entry : semantic.getObjects().entrySet()) {
                String objectName = entry.getKey();
                SemanticObjectModel semanticObj = entry.getValue();
                FlatObjectModel flatObj = toFlatObject(semanticObj);
                flat.addObject(objectName, flatObj);
            }
        }
        
        log.debug("[Adapter] Converted {} objects", flat.getObjects().size());
        
        return flat;
    }

    /**
     * Convert SemanticObjectModel to FlatObjectModel
     */
    public static FlatObjectModel toFlatObject(SemanticObjectModel semantic) {
        if (semantic == null) {
            return null;
        }
        
        FlatObjectModel flat = new FlatObjectModel();
        flat.setKind(semantic.getKind());
        flat.setApiVersion(semantic.getApiVersion());
        flat.setName(semantic.getName());
        flat.setNamespace(semantic.getNamespace());
        
        // Flatten metadata
        Map<String, String> flatMetadata = flattenStructure("", semantic.getMetadata());
        flat.setMetadata(flatMetadata);
        
        // Flatten spec
        Map<String, String> flatSpec = flattenStructure("", semantic.getSpec());
        flat.setSpec(flatSpec);
        
        return flat;
    }

    /**
     * Flatten nested structure to dot-notation with identity-based list indexing
     * Key difference from original: Uses identity for list items instead of numeric index
     * Examples:
     *   - containers[nginx].image instead of containers[0].image
     *   - volumes[my-vol].name instead of volumes[1].name
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> flattenStructure(String prefix, Object value) {
        Map<String, String> result = new HashMap<>();
        
        if (value == null) {
            return result;
        }
        
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                String newPrefix = prefix.isEmpty() ? key : prefix + "." + key;
                result.putAll(flattenStructure(newPrefix, entry.getValue()));
            }
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            
            // Check if structured list (list of objects with identity)
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                // Use identity-based indexing
                for (Object item : list) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    String identity = SemanticObjectModel.getIdentityValue(itemMap);
                    String newPrefix = prefix + "[" + identity + "]";
                    result.putAll(flattenStructure(newPrefix, item));
                }
            } else {
                // Simple list - use numeric index
                for (int i = 0; i < list.size(); i++) {
                    String newPrefix = prefix + "[" + i + "]";
                    result.putAll(flattenStructure(newPrefix, list.get(i)));
                }
            }
        } else {
            // Leaf value
            result.put(prefix, String.valueOf(value));
        }
        
        return result;
    }

    /**
     * Convert FlatNamespaceModel to SemanticNamespaceModel (reverse direction)
     * Used when consuming flat data and need to work with V2 comparator
     */
    public static SemanticNamespaceModel toSemanticModel(FlatNamespaceModel flat) {
        if (flat == null) {
            return null;
        }
        
        log.debug("[Adapter] Converting flat to semantic model: {}", flat.getName());
        
        SemanticNamespaceModel semantic = new SemanticNamespaceModel();
        semantic.setName(flat.getName());
        semantic.setClusterName(flat.getClusterName());
        semantic.setObjects(new HashMap<>());
        
        // Convert each object
        if (flat.getObjects() != null) {
            for (Map.Entry<String, FlatObjectModel> entry : flat.getObjects().entrySet()) {
                String objectName = entry.getKey();
                FlatObjectModel flatObj = entry.getValue();
                SemanticObjectModel semanticObj = toSemanticObject(flatObj);
                semantic.addObject(objectName, semanticObj);
            }
        }
        
        log.debug("[Adapter] Converted {} objects", semantic.getObjectCount());
        
        return semantic;
    }

    /**
     * Convert FlatObjectModel to SemanticObjectModel (reverse direction)
     */
    public static SemanticObjectModel toSemanticObject(FlatObjectModel flat) {
        if (flat == null) {
            return null;
        }
        
        SemanticObjectModel semantic = new SemanticObjectModel();
        semantic.setKind(flat.getKind());
        semantic.setApiVersion(flat.getApiVersion());
        semantic.setName(flat.getName());
        semantic.setNamespace(flat.getNamespace());
        
        // Unflatten metadata
        Map<String, Object> metadata = unflattenMap(flat.getMetadata());
        semantic.setMetadata(metadata);
        
        // Unflatten spec
        Map<String, Object> spec = unflattenMap(flat.getSpec());
        semantic.setSpec(spec);
        
        return semantic;
    }

    /**
     * Unflatten dot-notation map to nested structure
     * Handles both numeric indexes [0] and identity-based indexes [nginx]
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unflattenMap(Map<String, String> flat) {
        if (flat == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String path = entry.getKey();
            String value = entry.getValue();
            
            setNestedValue(result, path, value);
        }
        
        return result;
    }

    /**
     * Set a value in nested structure using path like "a.b[0].c"
     */
    @SuppressWarnings("unchecked")
    private static void setNestedValue(Map<String, Object> root, String path, String value) {
        String[] parts = splitPath(path);
        Map<String, Object> current = root;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            
            if (part.contains("[")) {
                // Handle array notation: "containers[nginx]" or "items[0]"
                String fieldName = part.substring(0, part.indexOf('['));
                String index = part.substring(part.indexOf('[') + 1, part.indexOf(']'));
                
                // Get or create list
                if (!current.containsKey(fieldName)) {
                    current.put(fieldName, new ArrayList<>());
                }
                
                Object fieldValue = current.get(fieldName);
                if (!(fieldValue instanceof List)) {
                    current.put(fieldName, new ArrayList<>());
                    fieldValue = current.get(fieldName);
                }
                
                List<Object> list = (List<Object>) fieldValue;
                
                // Try to parse as numeric index
                try {
                    int idx = Integer.parseInt(index);
                    // Ensure list is large enough
                    while (list.size() <= idx) {
                        list.add(new HashMap<String, Object>());
                    }
                    
                    if (!(list.get(idx) instanceof Map)) {
                        list.set(idx, new HashMap<String, Object>());
                    }
                    current = (Map<String, Object>) list.get(idx);
                } catch (NumberFormatException e) {
                    // Identity-based index - find or create item
                    Map<String, Object> item = findOrCreateListItem(list, index);
                    current = item;
                }
            } else {
                // Regular field
                if (!current.containsKey(part)) {
                    current.put(part, new HashMap<String, Object>());
                }
                
                Object next = current.get(part);
                if (!(next instanceof Map)) {
                    current.put(part, new HashMap<String, Object>());
                    next = current.get(part);
                }
                current = (Map<String, Object>) next;
            }
        }
        
        // Set the final value
        String lastPart = parts[parts.length - 1];
        current.put(lastPart, value);
    }

    /**
     * Find or create list item by identity
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findOrCreateListItem(List<Object> list, String identity) {
        // Try to find existing item with this identity
        for (Object obj : list) {
            if (obj instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) obj;
                String itemIdentity = SemanticObjectModel.getIdentityValue(map);
                if (identity.equals(itemIdentity)) {
                    return map;
                }
            }
        }
        
        // Create new item with identity as name
        Map<String, Object> newItem = new HashMap<>();
        newItem.put("name", identity);
        list.add(newItem);
        return newItem;
    }

    /**
     * Split path into parts, handling array notation
     */
    private static String[] splitPath(String path) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inBracket = false;
        
        for (char c : path.toCharArray()) {
            if (c == '[') {
                inBracket = true;
                current.append(c);
            } else if (c == ']') {
                inBracket = false;
                current.append(c);
            } else if (c == '.' && !inBracket) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts.toArray(new String[0]);
    }
}
