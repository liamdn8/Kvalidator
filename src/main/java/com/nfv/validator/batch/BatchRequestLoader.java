package com.nfv.validator.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.model.batch.BatchValidationRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads batch validation requests from JSON or YAML files
 */
@Slf4j
public class BatchRequestLoader {
    
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    
    public BatchRequestLoader() {
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        
        // Configure to handle Java 8 date/time types
        this.jsonMapper.findAndRegisterModules();
        this.yamlMapper.findAndRegisterModules();
    }
    
    /**
     * Load batch validation request from file (auto-detect format)
     * 
     * @param filePath Path to JSON or YAML file
     * @return BatchValidationRequest
     * @throws IOException if file cannot be read or parsed
     */
    public BatchValidationRequest load(String filePath) throws IOException {
        log.info("Loading batch request from: {}", filePath);
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + filePath);
        }
        
        File file = path.toFile();
        BatchValidationRequest request;
        
        // Auto-detect format based on file extension
        if (filePath.toLowerCase().endsWith(".json")) {
            request = loadFromJson(file);
        } else if (filePath.toLowerCase().endsWith(".yaml") || filePath.toLowerCase().endsWith(".yml")) {
            request = loadFromYaml(file);
        } else {
            // Try YAML first, then JSON
            try {
                request = loadFromYaml(file);
            } catch (Exception e) {
                log.debug("Failed to parse as YAML, trying JSON: {}", e.getMessage());
                request = loadFromJson(file);
            }
        }
        
        // Validate the loaded request
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid batch request: " + e.getMessage(), e);
        }
        
        log.info("Successfully loaded batch request with {} validation requests", 
                request.getRequests().size());
        
        return request;
    }
    
    /**
     * Load from JSON file
     */
    private BatchValidationRequest loadFromJson(File file) throws IOException {
        log.debug("Parsing as JSON: {}", file.getName());
        return jsonMapper.readValue(file, BatchValidationRequest.class);
    }
    
    /**
     * Load from YAML file
     */
    private BatchValidationRequest loadFromYaml(File file) throws IOException {
        log.debug("Parsing as YAML: {}", file.getName());
        return yamlMapper.readValue(file, BatchValidationRequest.class);
    }
    
    /**
     * Save batch validation request to file
     * Useful for generating templates or saving modified requests
     */
    public void save(BatchValidationRequest request, String filePath) throws IOException {
        log.info("Saving batch request to: {}", filePath);
        
        Path path = Paths.get(filePath);
        File file = path.toFile();
        
        // Create parent directories if needed
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        
        // Save based on file extension
        if (filePath.toLowerCase().endsWith(".json")) {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, request);
        } else {
            yamlMapper.writeValue(file, request);
        }
        
        log.info("Batch request saved successfully");
    }
}
