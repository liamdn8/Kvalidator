package com.nfv.validator.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * V2 YAML Data Collector - Collects YAML resources in semantic structure
 * Preserves nested lists and objects for accurate semantic comparison
 */
@Slf4j
public class YamlDataCollectorV2 {

    private final ObjectMapper yamlMapper;

    public YamlDataCollectorV2() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Collect resources from YAML in semantic structure
     */
    public SemanticNamespaceModel collectFromYaml(String path, String baselineName) throws IOException {
        log.info("[V2] Collecting YAML from: {} as '{}'", path, baselineName);
        
        SemanticNamespaceModel namespace = new SemanticNamespaceModel();
        namespace.setName(baselineName);
        namespace.setClusterName("baseline");
        namespace.setObjects(new HashMap<>());

        File file = new File(path);
        List<File> yamlFiles = new ArrayList<>();

        if (file.isDirectory()) {
            File[] files = file.listFiles((dir, name) -> isYamlFile(name));
            if (files != null) {
                yamlFiles.addAll(Arrays.asList(files));
            }
        } else if (isYamlFile(file.getName())) {
            yamlFiles.add(file);
        }

        int totalObjects = 0;
        for (File yamlFile : yamlFiles) {
            try {
                List<Map<String, Object>> docs = parseYamlFile(yamlFile);
                
                for (Map<String, Object> doc : docs) {
                    // Handle Kubernetes List objects
                    if ("List".equals(doc.get("kind")) && doc.containsKey("items")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                        log.debug("[V2] Processing List with {} items", items.size());
                        for (Map<String, Object> item : items) {
                            SemanticObjectModel obj = convertToSemanticObject(item);
                            if (obj != null) {
                                namespace.addObject(obj.getName(), obj);
                                totalObjects++;
                            }
                        }
                    } else {
                        // Process single resource
                        SemanticObjectModel obj = convertToSemanticObject(doc);
                        if (obj != null) {
                            namespace.addObject(obj.getName(), obj);
                            totalObjects++;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[V2] Failed to parse YAML file: {} - {}", yamlFile.getName(), e.getMessage());
            }
        }

        log.info("[V2] Collected {} objects from {} YAML files", totalObjects, yamlFiles.size());
        
        return namespace;
    }

    /**
     * Parse YAML file which may contain single or multiple documents
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseYamlFile(File file) throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        // Try to read all documents
        try {
            com.fasterxml.jackson.databind.MappingIterator<Map> iterator = 
                yamlMapper.readerFor(Map.class).readValues(file);
            while (iterator.hasNext()) {
                Map<String, Object> doc = (Map<String, Object>) iterator.next();
                documents.add(doc);
            }
        } catch (Exception e) {
            // If multi-doc fails, try single document
            Map<String, Object> singleDoc = yamlMapper.readValue(file, Map.class);
            if (singleDoc != null) {
                documents.add(singleDoc);
            }
        }
        
        return documents;
    }

    /**
     * Convert YAML document to SemanticObjectModel (preserves nested structure)
     */
    @SuppressWarnings("unchecked")
    private SemanticObjectModel convertToSemanticObject(Map<String, Object> doc) {
        // Must have kind
        if (!doc.containsKey("kind")) {
            log.debug("[V2] Skipping document without kind");
            return null;
        }

        String kind = doc.get("kind").toString();
        
        // Handle Kubernetes List objects specially
        if ("List".equals(kind) && doc.containsKey("items")) {
            log.debug("[V2] Skipping List - items will be processed separately");
            return null;
        }
        
        // Must have metadata
        if (!doc.containsKey("metadata")) {
            log.debug("[V2] Skipping document without metadata");
            return null;
        }

        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        
        if (metadata == null || !metadata.containsKey("name")) {
            log.debug("[V2] Skipping {} without metadata.name", kind);
            return null;
        }

        String name = metadata.get("name").toString();
        SemanticObjectModel obj = new SemanticObjectModel();
        obj.setKind(kind);
        obj.setName(name);
        obj.setApiVersion(doc.containsKey("apiVersion") ? doc.get("apiVersion").toString() : "");
        obj.setNamespace("baseline");
        
        // Store metadata as nested structure (no flattening!)
        obj.setMetadata(new HashMap<>(metadata));
        
        // Store spec as nested structure (preserves lists!)
        Map<String, Object> spec = new HashMap<>();
        if (doc.containsKey("spec")) {
            Object specObj = doc.get("spec");
            if (specObj instanceof Map) {
                spec = new HashMap<>((Map<String, Object>) specObj);
            }
        }
        
        // For ConfigMap/Secret, include data
        if (doc.containsKey("data")) {
            Object dataObj = doc.get("data");
            if (dataObj instanceof Map) {
                spec.put("data", new HashMap<>((Map<String, Object>) dataObj));
            }
        }
        
        obj.setSpec(spec);
        
        log.debug("[V2] Converted {} '{}' (semantic mode)", kind, name);
        
        return obj;
    }

    /**
     * Check if file is a YAML file by extension
     */
    private boolean isYamlFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }
}
