package com.nfv.validator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loads validation configuration from YAML files
 */
@Slf4j
public class ConfigLoader {
    
    private static final String DEFAULT_CONFIG = "validation-config.yaml";
    private final ObjectMapper yamlMapper;
    
    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * Load configuration from default classpath resource
     */
    public ValidationConfig loadDefault() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG)) {
            if (is == null) {
                log.warn("Default config file '{}' not found, using empty config", DEFAULT_CONFIG);
                return new ValidationConfig();
            }
            
            ValidationConfig config = yamlMapper.readValue(is, ValidationConfig.class);
            log.info("Loaded default validation config with {} ignore fields", 
                    config.getIgnoreFields().size());
            return config;
            
        } catch (Exception e) {
            log.error("Failed to load default config, using empty config", e);
            return new ValidationConfig();
        }
    }
    
    /**
     * Load configuration from a specific file path
     */
    public ValidationConfig loadFromFile(String filePath) {
        try {
            File configFile = new File(filePath);
            if (!configFile.exists()) {
                log.error("Config file not found: {}", filePath);
                return new ValidationConfig();
            }
            
            ValidationConfig config = yamlMapper.readValue(configFile, ValidationConfig.class);
            log.info("Loaded validation config from '{}' with {} ignore fields", 
                    filePath, config.getIgnoreFields().size());
            return config;
            
        } catch (Exception e) {
            log.error("Failed to load config from file: {}", filePath, e);
            return new ValidationConfig();
        }
    }
    
    /**
     * Load configuration from file path, or use default file in current directory
     */
    public ValidationConfig load(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            // Try to load from current directory first
            String defaultPath = DEFAULT_CONFIG;
            if (Files.exists(Paths.get(defaultPath))) {
                log.info("Loading config from current directory: {}", defaultPath);
                return loadFromFile(defaultPath);
            }
            // Fall back to classpath
            log.info("No config file in current directory, using default from classpath");
            return loadDefault();
        }
        
        if (!Files.exists(Paths.get(filePath))) {
            log.warn("Config file '{}' not found, using empty config", filePath);
            return new ValidationConfig();
        }
        
        return loadFromFile(filePath);
    }
}
