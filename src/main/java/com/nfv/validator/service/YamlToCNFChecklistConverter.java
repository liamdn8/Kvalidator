package com.nfv.validator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.model.cnf.NamespaceInfo;
import com.nfv.validator.model.cnf.YamlFileEntry;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.*;

/**
 * Service to convert Kubernetes YAML files to CNF Checklist items
 * Extracts important fields from each resource for validation
 */
@Slf4j
@ApplicationScoped
public class YamlToCNFChecklistConverter {

    private final ObjectMapper yamlMapper;

    public YamlToCNFChecklistConverter() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Extract namespace information from YAML content for smart search
     */
    public List<NamespaceInfo> extractNamespaces(String yamlContent) throws IOException {
        log.info("Extracting namespace information from YAML content");
        
        List<Map<String, Object>> documents = parseYamlContent(yamlContent);
        Map<String, NamespaceInfo> namespaceMap = new HashMap<>();
        
        for (Map<String, Object> doc : documents) {
            String namespace = extractNamespaceFromDoc(doc);
            String kind = (String) doc.get("kind");
            
            if (namespace == null || kind == null) {
                continue;
            }
            
            NamespaceInfo info = namespaceMap.computeIfAbsent(namespace, ns -> 
                NamespaceInfo.builder()
                    .name(ns)
                    .resourceCount(0)
                    .resourceKinds("")
                    .build()
            );
            
            info.setResourceCount(info.getResourceCount() + 1);
            
            // Add kind to list if not already present
            Set<String> kinds = new LinkedHashSet<>();
            if (!info.getResourceKinds().isEmpty()) {
                kinds.addAll(Arrays.asList(info.getResourceKinds().split(", ")));
            }
            kinds.add(kind);
            info.setResourceKinds(String.join(", ", kinds));
        }
        
        List<NamespaceInfo> result = new ArrayList<>(namespaceMap.values());
        result.sort(Comparator.comparing(NamespaceInfo::getName));
        
        log.info("Found {} namespaces", result.size());
        return result;
    }

    /**
     * Convert YAML content to CNF Checklist items
     * Always override namespace with targetNamespace (like kubectl -n behavior)
     */
    public List<CNFChecklistItem> convertToCNFChecklist(String vimName, String yamlContent, 
                                                        List<String> targetNamespaces,
                                                        List<String> customImportantFields) throws IOException {
        log.info("Converting YAML to CNF checklist items for vimName: {}", vimName);
        
        List<Map<String, Object>> documents = parseYamlContent(yamlContent);
        List<CNFChecklistItem> checklistItems = new ArrayList<>();
        
        // Determine namespace to inject (like kubectl -n <namespace>)
        // Always override namespace from YAML with this value
        String namespace = "default";
        if (targetNamespaces != null && !targetNamespaces.isEmpty()) {
            namespace = targetNamespaces.get(0);
        }
        
        log.info("Will inject namespace '{}' for all resources (overriding YAML namespace if present)", namespace);
        
        for (Map<String, Object> doc : documents) {
            String kind = (String) doc.get("kind");
            
            if (kind == null) {
                log.debug("Skipping document without kind");
                continue;
            }
            
            // Always use injected namespace, ignore namespace from YAML
            log.debug("Processing {} resource with namespace '{}'", kind, namespace);
            
            // Extract checklist items from this resource (pass namespace)
            List<CNFChecklistItem> items = extractChecklistItems(vimName, namespace, doc, customImportantFields);
            checklistItems.addAll(items);
        }
        
        log.info("Generated {} CNF checklist items", checklistItems.size());
        return checklistItems;
    }

    /**
     * Parse YAML content which may contain multiple documents
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseYamlContent(String yamlContent) throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        // Split by document separator ---
        String[] docs = yamlContent.split("---");
        
        for (String doc : docs) {
            doc = doc.trim();
            if (doc.isEmpty() || doc.startsWith("#")) {
                continue;
            }
            
            try {
                Map<String, Object> parsed = yamlMapper.readValue(doc, Map.class);
                if (parsed != null && !parsed.isEmpty()) {
                    // Handle Kubernetes List objects
                    if ("List".equals(parsed.get("kind")) && parsed.containsKey("items")) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) parsed.get("items");
                        documents.addAll(items);
                    } else {
                        documents.add(parsed);
                    }
                }
            } catch (Exception e) {
                log.debug("Skipping invalid YAML document: {}", e.getMessage());
            }
        }
        
        return documents;
    }

    /**
     * Extract namespace from a Kubernetes resource document
     * Returns the namespace from YAML if present, otherwise returns null
     */
    @SuppressWarnings("unchecked")
    private String extractNamespaceFromDoc(Map<String, Object> doc) {
        if (doc.containsKey("metadata")) {
            Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
            if (metadata != null && metadata.containsKey("namespace")) {
                return (String) metadata.get("namespace");
            }
        }
        // Return null if not specified (will be injected later)
        return null;
    }

    /**
     * Extract checklist items from a single Kubernetes resource
     * Extracts ALL fields from the YAML document recursively
     */
    @SuppressWarnings("unchecked")
    private List<CNFChecklistItem> extractChecklistItems(String vimName, String namespace, 
                                                         Map<String, Object> doc,
                                                         List<String> customImportantFields) {
        List<CNFChecklistItem> items = new ArrayList<>();
        
        String kind = (String) doc.get("kind");
        
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        if (metadata == null || !metadata.containsKey("name")) {
            return items;
        }
        
        String objectName = (String) metadata.get("name");
        
        // Extract all fields recursively from the document
        extractAllFieldsRecursive(doc, "", items, vimName, namespace, kind, objectName);
        
        return items;
    }
    
    /**
     * Recursively extract all fields from a YAML document
     */
    @SuppressWarnings("unchecked")
    private void extractAllFieldsRecursive(Object current, String currentPath, 
                                          List<CNFChecklistItem> items,
                                          String vimName, String namespace, 
                                          String kind, String objectName) {
        if (current == null) {
            return;
        }
        
        if (current instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) current;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                
                // Skip metadata.name and metadata.namespace (already in separate columns)
                // Also skip apiVersion and kind (system fields)
                if ("metadata.name".equals(newPath) || 
                    "metadata.namespace".equals(newPath) ||
                    "apiVersion".equals(newPath) ||
                    "kind".equals(newPath)) {
                    continue;
                }
                
                Object value = entry.getValue();
                
                // If value is primitive or simple type, add as item
                if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                    String valueStr = value != null ? value.toString() : "";
                    
                    // Skip if value is empty or blank (only export non-empty values to Excel)
                    if (valueStr.trim().isEmpty()) {
                        continue;
                    }
                    
                    items.add(CNFChecklistItem.builder()
                        .vimName(vimName)
                        .namespace(namespace)
                        .kind(kind)
                        .objectName(objectName)
                        .fieldKey(newPath)
                        .manoValue(valueStr)
                        .build());
                } else if (value instanceof List) {
                    // Handle list - recurse into each element
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < list.size(); i++) {
                        String arrayPath = newPath + "[" + i + "]";
                        extractAllFieldsRecursive(list.get(i), arrayPath, items, vimName, namespace, kind, objectName);
                    }
                } else if (value instanceof Map) {
                    // Recurse into nested map
                    extractAllFieldsRecursive(value, newPath, items, vimName, namespace, kind, objectName);
                }
            }
        } else if (current instanceof String || current instanceof Number || current instanceof Boolean) {
            // Leaf value - add as item
            String valueStr = current.toString();
            
            // Skip if value is empty or blank (only export non-empty values to Excel)
            if (valueStr.trim().isEmpty()) {
                return;
            }
            
            items.add(CNFChecklistItem.builder()
                .vimName(vimName)
                .namespace(namespace)
                .kind(kind)
                .objectName(objectName)
                .fieldKey(currentPath)
                .manoValue(valueStr)
                .build());
        }
    }

    /**
     * Get nested value from document using dot notation path
     * Supports array indexing: containers[0].image
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> doc, String path) {
        String[] parts = path.split("\\.");
        Object current = doc;
        
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            
            // Handle array indexing: containers[0]
            if (part.contains("[") && part.contains("]")) {
                int bracketIndex = part.indexOf("[");
                String arrayName = part.substring(0, bracketIndex);
                int index = Integer.parseInt(part.substring(bracketIndex + 1, part.length() - 1));
                
                if (current instanceof Map) {
                    Object arrayObj = ((Map<String, Object>) current).get(arrayName);
                    if (arrayObj instanceof List) {
                        List<?> list = (List<?>) arrayObj;
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Regular map access
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }
        }
        
        return current;
    }

    /**
     * Get nested map from document using dot notation path
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> doc, String path) {
        Object value = getNestedValue(doc, path);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * Extract namespaces from multiple YAML files (for batch conversion)
     */
    public List<NamespaceInfo> extractNamespacesFromMultipleFiles(List<YamlFileEntry> yamlFiles) throws IOException {
        log.info("Extracting namespace information from {} YAML files", yamlFiles.size());
        
        Map<String, NamespaceInfo> namespaceMap = new HashMap<>();
        
        for (YamlFileEntry fileEntry : yamlFiles) {
            List<Map<String, Object>> documents = parseYamlContent(fileEntry.getYamlContent());
            
            for (Map<String, Object> doc : documents) {
                String namespace = extractNamespaceFromDoc(doc);
                String kind = (String) doc.get("kind");
                
                if (namespace == null || kind == null) {
                    continue;
                }
                
                NamespaceInfo info = namespaceMap.computeIfAbsent(namespace, ns -> 
                    NamespaceInfo.builder()
                        .name(ns)
                        .resourceCount(0)
                        .resourceKinds("")
                        .build()
                );
                
                info.setResourceCount(info.getResourceCount() + 1);
                
                // Add kind to list if not already present
                Set<String> kinds = new LinkedHashSet<>();
                if (!info.getResourceKinds().isEmpty()) {
                    kinds.addAll(Arrays.asList(info.getResourceKinds().split(", ")));
                }
                kinds.add(kind);
                info.setResourceKinds(String.join(", ", kinds));
            }
        }
        
        List<NamespaceInfo> result = new ArrayList<>(namespaceMap.values());
        result.sort(Comparator.comparing(NamespaceInfo::getName));
        
        log.info("Found {} namespaces across all files", result.size());
        return result;
    }

    /**
     * Convert multiple YAML files to CNF Checklist items (batch conversion)
     */
    public List<CNFChecklistItem> convertMultipleFilesToCNFChecklist(
            String vimName, 
            List<YamlFileEntry> yamlFiles,
            List<String> targetNamespaces,
            List<String> customImportantFields) throws IOException {
        
        log.info("Converting {} YAML files to CNF checklist items for vimName: {}", 
                yamlFiles.size(), vimName);
        
        List<CNFChecklistItem> allChecklistItems = new ArrayList<>();
        
        for (YamlFileEntry fileEntry : yamlFiles) {
            log.debug("Processing file: {}", fileEntry.getFileName());
            
            List<CNFChecklistItem> fileItems = convertToCNFChecklist(
                vimName,
                fileEntry.getYamlContent(),
                targetNamespaces,
                customImportantFields
            );
            
            allChecklistItems.addAll(fileItems);
        }
        
        log.info("Generated total {} CNF checklist items from {} files", 
                allChecklistItems.size(), yamlFiles.size());
        
        return allChecklistItems;
    }
}
