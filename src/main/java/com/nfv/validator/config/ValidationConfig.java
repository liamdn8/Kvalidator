package com.nfv.validator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for validation and comparison
 * Defines fields to ignore during comparison
 */
@Data
@Slf4j
public class ValidationConfig {
    
    /**
     * List of field paths to ignore during comparison
     * Examples: "metadata.uid", "spec.clusterIP", "status"
     */
    private List<String> ignoreFields = new ArrayList<>();
    
    /**
     * Load configuration from YAML file
     */
    public static ValidationConfig loadFromYaml(String resourcePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        
        try (InputStream is = ValidationConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Config file not found: {}, using default config", resourcePath);
                return new ValidationConfig();
            }
            
            ValidationConfig config = mapper.readValue(is, ValidationConfig.class);
            log.info("Loaded validation config with {} ignore rules", config.getIgnoreFields().size());
            return config;
        }
    }
    
    /**
     * Load default configuration from validation-config.yaml
     */
    public static ValidationConfig loadDefault() {
        try {
            return loadFromYaml("validation-config.yaml");
        } catch (IOException e) {
            log.warn("Failed to load default config: {}, using empty config", e.getMessage());
            return new ValidationConfig();
        }
    }
    
    /**
     * Check if a field should be ignored based on pattern matching
     * Supports multiple matching strategies:
     * - Exact match: "field.name" => matches exactly "field.name"
     * - Prefix match: "field.name*" => matches "field.name", "field.name.sub", "field.name[0]"
     * - Suffix match: "*field.name" => matches "any.field.name", "prefix.field.name"
     * - Contains match: "*field.name*" => matches any path containing "field.name"
     * - Auto-prefix: "field.name" (without wildcard) also matches "field.name.sub", "field.name[0]"
     * 
     * @param fieldPath Full field path (e.g., "metadata.uid", "spec.replicas")
     * @return true if field should be ignored
     */
    public boolean shouldIgnore(String fieldPath) {
        if (fieldPath == null || ignoreFields == null) {
            return false;
        }
        
        for (String ignorePattern : ignoreFields) {
            if (ignorePattern == null || ignorePattern.isEmpty()) {
                continue;
            }
            
            // Check if pattern uses wildcards (support both * and .* for compatibility)
            boolean startsWithWildcard = ignorePattern.startsWith("*") || ignorePattern.startsWith(".*");
            boolean endsWithWildcard = ignorePattern.endsWith("*") || ignorePattern.endsWith(".*");
            
            // Normalize pattern by removing leading/trailing wildcards
            String normalizedPattern = ignorePattern;
            int startOffset = 0;
            int endOffset = 0;
            
            if (ignorePattern.startsWith("*")) {
                startOffset = 1;
            } else if (ignorePattern.startsWith(".*")) {
                startOffset = 2;
            }
            
            if (ignorePattern.endsWith("*") && !ignorePattern.endsWith(".*")) {
                endOffset = 1;
            } else if (ignorePattern.endsWith(".*")) {
                endOffset = 2;
            }
            
            if (startsWithWildcard && endsWithWildcard) {
                // Contains match: *field.name* or .*field.name.*
                String containsPattern = ignorePattern.substring(startOffset, ignorePattern.length() - endOffset);
                if (fieldPath.contains(containsPattern)) {
                    log.debug("Field '{}' ignored by contains pattern '{}'", fieldPath, ignorePattern);
                    return true;
                }
            } else if (startsWithWildcard) {
                // Suffix match: *field.name or .*field.name
                String suffixPattern = ignorePattern.substring(startOffset);
                if (fieldPath.endsWith(suffixPattern)) {
                    log.debug("Field '{}' ignored by suffix pattern '{}'", fieldPath, ignorePattern);
                    return true;
                }
            } else if (endsWithWildcard) {
                // Prefix match: field.name* or field.name.*
                String prefixPattern = ignorePattern.substring(0, ignorePattern.length() - endOffset);
                if (fieldPath.equals(prefixPattern) || 
                    fieldPath.startsWith(prefixPattern + ".") || 
                    fieldPath.startsWith(prefixPattern + "[")) {
                    log.debug("Field '{}' ignored by prefix pattern '{}'", fieldPath, ignorePattern);
                    return true;
                }
            } else {
                // Exact match OR auto-prefix match (for backward compatibility)
                // "metadata.annotations" matches:
                //   - "metadata.annotations" (exact)
                //   - "metadata.annotations.key" (auto-prefix)
                //   - "metadata.annotations[0]" (auto-prefix)
                if (fieldPath.equals(ignorePattern) || 
                    fieldPath.startsWith(ignorePattern + ".") || 
                    fieldPath.startsWith(ignorePattern + "[")) {
                    log.debug("Field '{}' ignored by exact/auto-prefix pattern '{}'", fieldPath, ignorePattern);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Add a field to ignore list
     */
    public void addIgnoreField(String fieldPath) {
        if (!ignoreFields.contains(fieldPath)) {
            ignoreFields.add(fieldPath);
        }
    }
    
    /**
     * Filter ignored fields from FlatObjectModel
     * Removes all fields matching ignore patterns to improve comparison speed
     * and prevent ignored fields from appearing in comparison results
     * 
     * @param object FlatObjectModel to filter
     */
    public void filterIgnoredFields(com.nfv.validator.model.FlatObjectModel object) {
        if (object == null || ignoreFields == null || ignoreFields.isEmpty()) {
            return;
        }
        
        int removedCount = 0;
        
        // Filter metadata fields
        if (object.getMetadata() != null) {
            java.util.Iterator<String> metadataIterator = object.getMetadata().keySet().iterator();
            while (metadataIterator.hasNext()) {
                String key = metadataIterator.next();
                String fullPath = "metadata." + key;
                if (shouldIgnore(fullPath)) {
                    metadataIterator.remove();
                    removedCount++;
                    log.debug("Removed ignored field from metadata: {}", fullPath);
                }
            }
        }
        
        // Filter spec fields
        if (object.getSpec() != null) {
            java.util.Iterator<String> specIterator = object.getSpec().keySet().iterator();
            while (specIterator.hasNext()) {
                String key = specIterator.next();
                String fullPath = "spec." + key;
                if (shouldIgnore(fullPath)) {
                    specIterator.remove();
                    removedCount++;
                    log.debug("Removed ignored field from spec: {}", fullPath);
                }
            }
        }
        
        if (removedCount > 0) {
            log.debug("Filtered {} ignored fields from object: {}", removedCount, object.getName());
        }
    }
    
    /**
     * Filter ignored fields from FlatNamespaceModel
     * Removes all fields matching ignore patterns from all objects in the namespace
     * 
     * @param namespaceModel FlatNamespaceModel to filter
     */
    public void filterIgnoredFields(com.nfv.validator.model.FlatNamespaceModel namespaceModel) {
        if (namespaceModel == null || namespaceModel.getObjects() == null) {
            return;
        }
        
        int totalRemoved = 0;
        for (com.nfv.validator.model.FlatObjectModel object : namespaceModel.getObjects().values()) {
            int beforeMetadata = object.getMetadata() != null ? object.getMetadata().size() : 0;
            int beforeSpec = object.getSpec() != null ? object.getSpec().size() : 0;
            
            filterIgnoredFields(object);
            
            int afterMetadata = object.getMetadata() != null ? object.getMetadata().size() : 0;
            int afterSpec = object.getSpec() != null ? object.getSpec().size() : 0;
            
            totalRemoved += (beforeMetadata - afterMetadata) + (beforeSpec - afterSpec);
        }
        
        if (totalRemoved > 0) {
            log.info("Filtered {} ignored fields from namespace: {} ({} objects)", 
                    totalRemoved, namespaceModel.getName(), namespaceModel.getObjects().size());
        }
    }
}
