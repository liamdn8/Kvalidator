package com.nfv.validator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nfv.validator.model.api.ValidationResultJson;
import com.nfv.validator.model.comparison.NamespaceComparison;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.time.Instant;
import java.util.Map;

/**
 * Service for exporting validation results to JSON format
 */
@Slf4j
@ApplicationScoped
public class JsonResultExporter {
    
    private final ObjectMapper objectMapper;
    
    public JsonResultExporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Export validation results to JSON file
     * 
     * @param jobId Job identifier
     * @param comparisons Map of comparison results
     * @param description Optional description
     * @param outputFile Output file path
     */
    public void exportToJson(String jobId, 
                            Map<String, NamespaceComparison> comparisons,
                            String description,
                            File outputFile) throws Exception {
        
        log.info("Exporting validation results to JSON: {}", outputFile.getAbsolutePath());
        
        // Calculate summary statistics
        int totalObjects = 0;
        int totalDifferences = 0;
        
        for (NamespaceComparison comparison : comparisons.values()) {
            totalObjects += comparison.getSummary().getCommonObjects();
            totalDifferences += comparison.getSummary().getDifferencesCount();
        }
        
        // Build result object
        ValidationResultJson.SummaryStats summary = ValidationResultJson.SummaryStats.builder()
                .totalObjects(totalObjects)
                .totalDifferences(totalDifferences)
                .namespacePairs(comparisons.size())
                .executionTimeMs(0) // Will be calculated by caller
                .build();
        
        ValidationResultJson result = ValidationResultJson.builder()
                .jobId(jobId)
                .submittedAt(Instant.now())
                .completedAt(Instant.now())
                .description(description)
                .summary(summary)
                .comparisons(comparisons)
                .build();
        
        // Write to file
        objectMapper.writeValue(outputFile, result);
        
        log.info("Successfully exported JSON results: {} objects, {} differences", 
                totalObjects, totalDifferences);
    }
}
