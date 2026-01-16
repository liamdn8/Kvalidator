package com.nfv.validator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.config.ValidationConfig;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service for managing validation configuration
 */
@Slf4j
@ApplicationScoped
public class ValidationConfigService {
    
    private static final String CONFIG_FILE = "validation-config.yaml";
    private static final String CONFIG_BACKUP = "validation-config.yaml.backup";
    
    private final ObjectMapper yamlMapper;
    
    public ValidationConfigService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * Load current validation configuration
     * Tries to load from current directory first, then falls back to classpath
     */
    public ValidationConfig loadConfig() {
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            try {
                ValidationConfig config = yamlMapper.readValue(configPath.toFile(), ValidationConfig.class);
                log.info("Loaded validation config from file: {} with {} ignore rules", 
                        configPath, config.getIgnoreFields().size());
                return config;
            } catch (IOException e) {
                log.error("Failed to load config from file, falling back to classpath", e);
            }
        }
        
        // Fall back to classpath
        return loadDefaultConfig();
    }
    
    /**
     * Load default configuration from classpath
     */
    public ValidationConfig loadDefaultConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                log.warn("Default config file not found in classpath, creating empty config");
                return new ValidationConfig();
            }
            
            ValidationConfig config = yamlMapper.readValue(is, ValidationConfig.class);
            log.info("Loaded default validation config from classpath with {} ignore rules", 
                    config.getIgnoreFields().size());
            return config;
        } catch (IOException e) {
            log.error("Failed to load default config from classpath", e);
            return new ValidationConfig();
        }
    }
    
    /**
     * Save validation configuration to file
     */
    public void saveConfig(ValidationConfig config) throws IOException {
        Path configPath = Paths.get(CONFIG_FILE);
        Path backupPath = Paths.get(CONFIG_BACKUP);
        
        // Create backup of existing config
        if (Files.exists(configPath)) {
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Created backup at: {}", backupPath);
        }
        
        // Write new config
        yamlMapper.writeValue(configPath.toFile(), config);
        log.info("Saved validation config to: {} with {} ignore rules", 
                configPath, config.getIgnoreFields().size());
    }
    
    /**
     * Export configuration as YAML string
     */
    public String exportConfigAsYaml() throws IOException {
        ValidationConfig config = loadConfig();
        return yamlMapper.writeValueAsString(config);
    }
    
    /**
     * Import configuration from YAML string
     */
    public ValidationConfig importConfigFromYaml(String yamlContent) throws IOException {
        return yamlMapper.readValue(yamlContent, ValidationConfig.class);
    }
    
    /**
     * Restore configuration from backup
     */
    public boolean restoreFromBackup() {
        Path backupPath = Paths.get(CONFIG_BACKUP);
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (!Files.exists(backupPath)) {
            log.warn("Backup file not found: {}", backupPath);
            return false;
        }
        
        try {
            Files.copy(backupPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Restored config from backup");
            return true;
        } catch (IOException e) {
            log.error("Failed to restore from backup", e);
            return false;
        }
    }
}
