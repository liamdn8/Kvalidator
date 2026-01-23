package com.nfv.validator.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects Kubernetes resources from YAML files (baseline/design documents)
 */
@Slf4j
public class YamlDataCollector {

    private final ObjectMapper yamlMapper;

    public YamlDataCollector() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Collect resources from a YAML file or directory
     * 
     * @param path Path to YAML file or directory containing YAML files
     * @param baselineName Name for the baseline (e.g., "design", "baseline")
     * @return FlatNamespaceModel containing all resources from YAML files
     */
    public FlatNamespaceModel collectFromYaml(String path, String baselineName) throws IOException {
        log.info("Collecting baseline from path: {}", path);
        
        Path inputPath = Paths.get(path);
        if (!Files.exists(inputPath)) {
            throw new IOException("Path does not exist: " + path);
        }

        List<File> yamlFiles = new ArrayList<>();
        
        if (Files.isDirectory(inputPath)) {
            // Collect all YAML files from directory
            try (Stream<Path> paths = Files.walk(inputPath)) {
                yamlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> isYamlFile(p.toString()))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            }
            log.debug("Found {} YAML files in directory: {}", yamlFiles.size(), path);
        } else if (isYamlFile(path)) {
            yamlFiles.add(inputPath.toFile());
            log.debug("Processing single YAML file: {}", path);
        } else {
            throw new IOException("Path is not a YAML file or directory: " + path);
        }

        if (yamlFiles.isEmpty()) {
            throw new IOException("No YAML files found in: " + path);
        }

        // Parse all YAML files and collect resources
        return collectFromYamlFiles(yamlFiles, baselineName);
    }

    /**
     * Collect resources from YAML content string
     * 
     * @param yamlContent YAML content as string (can contain multiple documents separated by ---)
     * @param baselineName Name for the baseline (e.g., "design", "baseline")
     * @return FlatNamespaceModel containing all resources from YAML content
     */
    public FlatNamespaceModel collectFromYamlContent(String yamlContent, String baselineName) throws IOException {
        log.info("Collecting baseline from YAML content string");
        
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new IOException("YAML content is empty");
        }

        FlatNamespaceModel namespace = new FlatNamespaceModel();
        namespace.setName(baselineName);
        namespace.setClusterName("baseline");
        namespace.setObjects(new HashMap<>());
        int totalObjects = 0;

        try {
            List<Map<String, Object>> documents = parseYamlContent(yamlContent);
            for (Map<String, Object> doc : documents) {
                // Check if this is a Kubernetes List object
                if ("List".equals(doc.get("kind")) && doc.containsKey("items")) {
                    // Process items array
                    List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                    log.debug("Processing List with {} items", items.size());
                    for (Map<String, Object> item : items) {
                        FlatObjectModel obj = convertToFlatObject(item);
                        if (obj != null) {
                            namespace.addObject(obj.getName(), obj);
                            totalObjects++;
                        }
                    }
                } else {
                    // Process single resource
                    FlatObjectModel obj = convertToFlatObject(doc);
                    if (obj != null) {
                        namespace.addObject(obj.getName(), obj);
                        totalObjects++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse YAML content: {}", e.getMessage());
            throw new IOException("Failed to parse YAML content: " + e.getMessage(), e);
        }

        log.info("Collected {} objects from YAML content", totalObjects);
        
        // Debug: print first object fields
        if (!namespace.getObjects().isEmpty()) {
            FlatObjectModel firstObj = namespace.getObjects().values().iterator().next();
            log.debug("Sample object {}/{}: {} metadata fields, {} spec fields, {} data fields", 
                firstObj.getKind(), firstObj.getName(), 
                firstObj.getMetadata().size(), firstObj.getSpec().size(),
                firstObj.getData() != null ? firstObj.getData().size() : 0);
            if (firstObj.getData() != null && !firstObj.getData().isEmpty()) {
                log.debug("Sample data keys: {}", firstObj.getData().keySet());
            }
        }
        
        return namespace;
    }

    /**
     * Common method to collect resources from list of YAML files
     */
    private FlatNamespaceModel collectFromYamlFiles(List<File> yamlFiles, String baselineName) throws IOException {
        FlatNamespaceModel namespace = new FlatNamespaceModel();
        namespace.setName(baselineName);
        namespace.setClusterName("baseline");
        namespace.setObjects(new HashMap<>());
        int totalObjects = 0;

        for (File file : yamlFiles) {
            try {
                List<Map<String, Object>> documents = parseYamlFile(file);
                for (Map<String, Object> doc : documents) {
                    // Check if this is a Kubernetes List object
                    if ("List".equals(doc.get("kind")) && doc.containsKey("items")) {
                        // Process items array
                        List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                        log.debug("Processing List with {} items", items.size());
                        for (Map<String, Object> item : items) {
                            FlatObjectModel obj = convertToFlatObject(item);
                            if (obj != null) {
                                namespace.addObject(obj.getName(), obj);
                                totalObjects++;
                            }
                        }
                    } else {
                        // Process single resource
                        FlatObjectModel obj = convertToFlatObject(doc);
                        if (obj != null) {
                            namespace.addObject(obj.getName(), obj);
                            totalObjects++;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse YAML file: {} - {}", file.getName(), e.getMessage());
            }
        }

        log.info("Collected {} objects from {} YAML files", totalObjects, yamlFiles.size());
        
        // Debug: print first object fields
        if (!namespace.getObjects().isEmpty()) {
            FlatObjectModel firstObj = namespace.getObjects().values().iterator().next();
            log.debug("Sample object {}/{}: {} metadata fields, {} spec fields, {} data fields", 
                firstObj.getKind(), firstObj.getName(), 
                firstObj.getMetadata().size(), firstObj.getSpec().size(),
                firstObj.getData() != null ? firstObj.getData().size() : 0);
            log.debug("Sample metadata keys: {}", firstObj.getMetadata().keySet());
            log.debug("Sample spec keys: {}", firstObj.getSpec().keySet());
            if (firstObj.getData() != null && !firstObj.getData().isEmpty()) {
                log.debug("Sample data keys: {}", firstObj.getData().keySet());
            }
        }
        
        return namespace;
    }

    /**
     * Parse YAML content string which may contain single or multiple documents
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
                    documents.add(parsed);
                }
            } catch (Exception e) {
                log.debug("Skipping invalid YAML document: {}", e.getMessage());
            }
        }
        
        return documents;
    }

    /**
     * Parse YAML file which may contain single or multiple documents
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseYamlFile(File file) throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        String content = new String(Files.readAllBytes(file.toPath()));
        return parseYamlContent(content);
    }

    /**
     * Convert parsed YAML document to FlatObjectModel
     * Handles both individual resources and Kubernetes List objects
     */
    @SuppressWarnings("unchecked")
    private FlatObjectModel convertToFlatObject(Map<String, Object> doc) {
        // Must have kind
        if (!doc.containsKey("kind")) {
            log.debug("Skipping document without kind");
            return null;
        }

        String kind = doc.get("kind").toString();
        
        // Handle Kubernetes List objects specially
        if ("List".equals(kind) && doc.containsKey("items")) {
            log.debug("Skipping List - items will be processed separately");
            return null;
        }
        
        // Must have metadata for non-List objects
        if (!doc.containsKey("metadata")) {
            log.debug("Skipping document without metadata");
            return null;
        }
        
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        
        if (metadata == null || !metadata.containsKey("name")) {
            log.debug("Skipping {} without metadata.name", kind);
            return null;
        }

        String name = metadata.get("name").toString();
        FlatObjectModel obj = new FlatObjectModel();
        obj.setKind(kind);
        obj.setName(name);
        obj.setApiVersion(doc.containsKey("apiVersion") ? doc.get("apiVersion").toString() : "");
        obj.setNamespace("baseline");
        obj.setMetadata(new HashMap<>());
        obj.setSpec(new HashMap<>());
        obj.setData(new HashMap<>());

        // Flatten metadata - store without "metadata." prefix 
        // (it will be added by getAllFields())
        if (metadata != null) {
            flattenMapWithPrefix("", metadata, obj, "metadata");
        }

        // Flatten spec - store without "spec." prefix
        if (doc.containsKey("spec")) {
            Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
            if (spec != null) {
                flattenMapWithPrefix("", spec, obj, "spec");
            }
        }
        
        // Flatten data - store without "data." prefix (for ConfigMap, Secret, etc.)
        if (doc.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) doc.get("data");
            if (data != null) {
                flattenMapWithPrefix("", data, obj, "data");
            }
        }
        
        // Flatten binaryData - store without "binaryData." prefix (for ConfigMap, Secret)
        if (doc.containsKey("binaryData")) {
            Map<String, Object> binaryData = (Map<String, Object>) doc.get("binaryData");
            if (binaryData != null) {
                flattenMapWithPrefix("", binaryData, obj, "binaryData");
            }
        }
        
        // Flatten stringData - store without "stringData." prefix (for Secret)
        if (doc.containsKey("stringData")) {
            Map<String, Object> stringData = (Map<String, Object>) doc.get("stringData");
            if (stringData != null) {
                flattenMapWithPrefix("", stringData, obj, "stringData");
            }
        }

        return obj;
    }
    
    /**
     * Flatten a map with a given prefix (for top-level metadata/spec/data)
     * @param fieldType One of: "metadata", "spec", "data", "binaryData", "stringData"
     */
    @SuppressWarnings("unchecked")
    private void flattenMapWithPrefix(String prefix, Map<String, Object> map, FlatObjectModel obj, String fieldType) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (value == null) {
                continue;
            }
            
            if (value instanceof Map) {
                flattenMapInternal(fullKey, (Map<String, Object>) value, obj, fieldType);
            } else if (value instanceof List) {
                flattenListInternal(fullKey, (List<?>) value, obj, fieldType);
            } else {
                addFieldByType(obj, fullKey, value.toString(), fieldType);
            }
        }
    }

    /**
     * Recursively flatten nested maps into dot-notation keys
     */
    @SuppressWarnings("unchecked")
    private void flattenMapInternal(String prefix, Map<String, Object> map, FlatObjectModel obj, String fieldType) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = prefix + "." + key;

            if (value == null) {
                continue;
            }

            if (value instanceof Map) {
                flattenMapInternal(fullKey, (Map<String, Object>) value, obj, fieldType);
            } else if (value instanceof List) {
                flattenListInternal(fullKey, (List<?>) value, obj, fieldType);
            } else {
                addFieldByType(obj, fullKey, value.toString(), fieldType);
            }
        }
    }

    /**
     * Recursively flatten nested lists into indexed keys
     */
    @SuppressWarnings("unchecked")
    private void flattenListInternal(String prefix, List<?> list, FlatObjectModel obj, String fieldType) {
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                flattenMapInternal(prefix + "[" + i + "]", (Map<String, Object>) item, obj, fieldType);
            } else {
                String itemKey = prefix + "[" + i + "]";
                String itemValue = item != null ? item.toString() : "null";
                addFieldByType(obj, itemKey, itemValue, fieldType);
            }
        }
    }
    
    /**
     * Add a field to the appropriate map based on field type
     */
    private void addFieldByType(FlatObjectModel obj, String key, String value, String fieldType) {
        switch (fieldType) {
            case "metadata":
                obj.addMetadata(key, value);
                break;
            case "spec":
                obj.addSpec(key, value);
                break;
            case "data":
            case "binaryData":
            case "stringData":
                // All data-related fields go to data map with appropriate prefix
                if (fieldType.equals("data")) {
                    obj.addData(key, value);
                } else {
                    obj.addData(fieldType + "." + key, value);
                }
                break;
            default:
                // Default to spec for unknown types
                obj.addSpec(key, value);
                break;
        }
    }

    /**
     * Check if file is a YAML file by extension
     */
    private boolean isYamlFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }
}
