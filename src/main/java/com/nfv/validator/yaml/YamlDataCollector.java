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
            log.debug("Sample object {}/{}: {} metadata fields, {} spec fields", 
                firstObj.getKind(), firstObj.getName(), 
                firstObj.getMetadata().size(), firstObj.getSpec().size());
            log.debug("Sample metadata keys: {}", firstObj.getMetadata().keySet());
            log.debug("Sample spec keys: {}", firstObj.getSpec().keySet());
        }
        
        return namespace;
    }

    /**
     * Parse YAML file which may contain single or multiple documents
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseYamlFile(File file) throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        String content = new String(Files.readAllBytes(file.toPath()));
        
        // Split by document separator ---
        String[] docs = content.split("---");
        
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
                log.debug("Skipping invalid YAML document in {}: {}", file.getName(), e.getMessage());
            }
        }
        
        return documents;
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

        // Flatten metadata - store without "metadata." prefix 
        // (it will be added by getAllFields())
        if (metadata != null) {
            flattenMapWithPrefix("", metadata, obj, true);
        }

        // Flatten spec - store without "spec." prefix
        if (doc.containsKey("spec")) {
            Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
            if (spec != null) {
                flattenMapWithPrefix("", spec, obj, false);
            }
        }

        return obj;
    }
    
    /**
     * Flatten a map with a given prefix (for top-level metadata/spec)
     */
    @SuppressWarnings("unchecked")
    private void flattenMapWithPrefix(String prefix, Map<String, Object> map, FlatObjectModel obj, boolean isMetadata) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (value == null) {
                continue;
            }
            
            if (value instanceof Map) {
                flattenMapInternal(fullKey, (Map<String, Object>) value, obj, isMetadata);
            } else if (value instanceof List) {
                flattenListInternal(fullKey, (List<?>) value, obj, isMetadata);
            } else {
                if (isMetadata) {
                    obj.addMetadata(fullKey, value.toString());
                } else {
                    obj.addSpec(fullKey, value.toString());
                }
            }
        }
    }

    /**
     * Recursively flatten nested maps into dot-notation keys
     */
    @SuppressWarnings("unchecked")
    private void flattenMapInternal(String prefix, Map<String, Object> map, FlatObjectModel obj, boolean isMetadata) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = prefix + "." + key;

            if (value == null) {
                continue;
            }

            if (value instanceof Map) {
                flattenMapInternal(fullKey, (Map<String, Object>) value, obj, isMetadata);
            } else if (value instanceof List) {
                flattenListInternal(fullKey, (List<?>) value, obj, isMetadata);
            } else {
                if (isMetadata) {
                    obj.addMetadata(fullKey, value.toString());
                } else {
                    obj.addSpec(fullKey, value.toString());
                }
            }
        }
    }

    /**
     * Recursively flatten nested lists into indexed keys
     */
    @SuppressWarnings("unchecked")
    private void flattenListInternal(String prefix, List<?> list, FlatObjectModel obj, boolean isMetadata) {
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                flattenMapInternal(prefix + "[" + i + "]", (Map<String, Object>) item, obj, isMetadata);
            } else {
                String itemKey = prefix + "[" + i + "]";
                String itemValue = item != null ? item.toString() : "null";
                if (isMetadata) {
                    obj.addMetadata(itemKey, itemValue);
                } else {
                    obj.addSpec(itemKey, itemValue);
                }
            }
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
