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
     * Check if a field should be ignored
     * @param fieldPath Full field path (e.g., "metadata.uid", "spec.replicas")
     * @return true if field should be ignored
     */
    public boolean shouldIgnore(String fieldPath) {
        if (fieldPath == null || ignoreFields == null) {
            return false;
        }
        
        // Direct match
        if (ignoreFields.contains(fieldPath)) {
            return true;
        }
        
        // Prefix match (e.g., "status" matches "status.replicas", "status.conditions[0].type")
        for (String ignorePattern : ignoreFields) {
            if (fieldPath.equals(ignorePattern) || fieldPath.startsWith(ignorePattern + ".") || fieldPath.startsWith(ignorePattern + "[")) {
                return true;
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
}
