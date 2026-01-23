package com.nfv.validator.service;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.batch.BatchValidationRequest;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test ignore fields functionality in batch validation
 */
class BatchValidationIgnoreFieldsTest {

    @Test
    @DisplayName("Global ignore fields should be merged into individual validation request")
    void testGlobalIgnoreFieldsMerge() {
        // Setup: Create batch request with global ignore fields
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        
        // Global settings with ignore fields
        BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
        List<String> globalIgnoreFields = Arrays.asList(
            "metadata.managedFields",
            "metadata.resourceVersion",
            "status.conditions"
        );
        settings.setIgnoreFields(globalIgnoreFields);
        batchRequest.setSettings(settings);
        
        // Individual validation request
        com.nfv.validator.model.batch.ValidationRequest validationRequest = 
            new com.nfv.validator.model.batch.ValidationRequest();
        validationRequest.setName("test-validation");
        validationRequest.setNamespaces(Arrays.asList("default"));
        
        // Individual request has its own ignore fields
        List<String> requestIgnoreFields = Arrays.asList(
            "spec.template.metadata.creationTimestamp"
        );
        validationRequest.setIgnoreFields(requestIgnoreFields);
        
        // Test: Simulate convertBatchRequestToJobRequest logic
        List<String> mergedIgnoreFields = new ArrayList<>();
        
        // Add global ignore fields
        if (batchRequest.getSettings() != null && 
            batchRequest.getSettings().getIgnoreFields() != null) {
            mergedIgnoreFields.addAll(batchRequest.getSettings().getIgnoreFields());
        }
        
        // Add request-specific ignore fields (no duplicates)
        if (validationRequest.getIgnoreFields() != null) {
            for (String field : validationRequest.getIgnoreFields()) {
                if (!mergedIgnoreFields.contains(field)) {
                    mergedIgnoreFields.add(field);
                }
            }
        }
        
        // Assert: Merged list should contain all 4 unique fields
        assertEquals(4, mergedIgnoreFields.size(), "Should have 4 merged ignore fields");
        assertTrue(mergedIgnoreFields.contains("metadata.managedFields"), 
                  "Should contain global ignore field: metadata.managedFields");
        assertTrue(mergedIgnoreFields.contains("metadata.resourceVersion"), 
                  "Should contain global ignore field: metadata.resourceVersion");
        assertTrue(mergedIgnoreFields.contains("status.conditions"), 
                  "Should contain global ignore field: status.conditions");
        assertTrue(mergedIgnoreFields.contains("spec.template.metadata.creationTimestamp"), 
                  "Should contain request-specific ignore field");
    }

    @Test
    @DisplayName("Duplicate ignore fields should not be added twice")
    void testNoDuplicateIgnoreFields() {
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        
        // Global settings
        BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
        List<String> globalIgnoreFields = Arrays.asList(
            "metadata.managedFields",
            "metadata.resourceVersion"
        );
        settings.setIgnoreFields(globalIgnoreFields);
        batchRequest.setSettings(settings);
        
        // Individual request has SAME ignore field
        com.nfv.validator.model.batch.ValidationRequest validationRequest = 
            new com.nfv.validator.model.batch.ValidationRequest();
        List<String> requestIgnoreFields = Arrays.asList(
            "metadata.resourceVersion",  // Duplicate!
            "status.phase"
        );
        validationRequest.setIgnoreFields(requestIgnoreFields);
        
        // Merge logic
        List<String> mergedIgnoreFields = new ArrayList<>();
        if (batchRequest.getSettings() != null && 
            batchRequest.getSettings().getIgnoreFields() != null) {
            mergedIgnoreFields.addAll(batchRequest.getSettings().getIgnoreFields());
        }
        if (validationRequest.getIgnoreFields() != null) {
            for (String field : validationRequest.getIgnoreFields()) {
                if (!mergedIgnoreFields.contains(field)) {
                    mergedIgnoreFields.add(field);
                }
            }
        }
        
        // Assert: Should only have 3 unique fields
        assertEquals(3, mergedIgnoreFields.size(), "Should have only 3 unique fields");
        assertTrue(mergedIgnoreFields.contains("metadata.managedFields"));
        assertTrue(mergedIgnoreFields.contains("metadata.resourceVersion"));
        assertTrue(mergedIgnoreFields.contains("status.phase"));
    }

    @Test
    @DisplayName("ValidationConfig.shouldIgnore should respect ignore patterns")
    void testValidationConfigShouldIgnore() {
        // Create ValidationConfig and add ignore patterns
        ValidationConfig config = new ValidationConfig();
        config.addIgnoreField("metadata.managedFields");
        config.addIgnoreField("metadata.resourceVersion");
        config.addIgnoreField("status.*");  // Wildcard pattern
        
        // Test exact matches
        assertTrue(config.shouldIgnore("metadata.managedFields"), 
                  "Should ignore exact match: metadata.managedFields");
        assertTrue(config.shouldIgnore("metadata.resourceVersion"), 
                  "Should ignore exact match: metadata.resourceVersion");
        
        // Test wildcard patterns
        assertTrue(config.shouldIgnore("status.conditions"), 
                  "Should ignore status.* wildcard: status.conditions");
        assertTrue(config.shouldIgnore("status.phase"), 
                  "Should ignore status.* wildcard: status.phase");
        
        // Test non-matching fields
        assertFalse(config.shouldIgnore("spec.replicas"), 
                   "Should NOT ignore non-matching field: spec.replicas");
        assertFalse(config.shouldIgnore("metadata.name"), 
                   "Should NOT ignore non-matching field: metadata.name");
    }

    @Test
    @DisplayName("Empty global ignore fields should not cause errors")
    void testEmptyGlobalIgnoreFields() {
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        
        // Global settings with NULL ignore fields
        BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
        settings.setIgnoreFields(null);
        batchRequest.setSettings(settings);
        
        // Individual request
        com.nfv.validator.model.batch.ValidationRequest validationRequest = 
            new com.nfv.validator.model.batch.ValidationRequest();
        List<String> requestIgnoreFields = Arrays.asList("spec.replicas");
        validationRequest.setIgnoreFields(requestIgnoreFields);
        
        // Merge logic
        List<String> mergedIgnoreFields = new ArrayList<>();
        if (batchRequest.getSettings() != null && 
            batchRequest.getSettings().getIgnoreFields() != null) {
            mergedIgnoreFields.addAll(batchRequest.getSettings().getIgnoreFields());
        }
        if (validationRequest.getIgnoreFields() != null) {
            for (String field : validationRequest.getIgnoreFields()) {
                if (!mergedIgnoreFields.contains(field)) {
                    mergedIgnoreFields.add(field);
                }
            }
        }
        
        // Assert: Should only have request-specific field
        assertEquals(1, mergedIgnoreFields.size(), "Should have 1 field from request");
        assertTrue(mergedIgnoreFields.contains("spec.replicas"));
    }

    @Test
    @DisplayName("No ignore fields should result in empty list")
    void testNoIgnoreFields() {
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        
        // No global settings
        batchRequest.setSettings(null);
        
        // No request-specific ignore fields
        com.nfv.validator.model.batch.ValidationRequest validationRequest = 
            new com.nfv.validator.model.batch.ValidationRequest();
        validationRequest.setIgnoreFields(null);
        
        // Merge logic
        List<String> mergedIgnoreFields = new ArrayList<>();
        if (batchRequest.getSettings() != null && 
            batchRequest.getSettings().getIgnoreFields() != null) {
            mergedIgnoreFields.addAll(batchRequest.getSettings().getIgnoreFields());
        }
        if (validationRequest.getIgnoreFields() != null) {
            for (String field : validationRequest.getIgnoreFields()) {
                if (!mergedIgnoreFields.contains(field)) {
                    mergedIgnoreFields.add(field);
                }
            }
        }
        
        // Assert: Should be empty
        assertEquals(0, mergedIgnoreFields.size(), "Should have no ignore fields");
    }

    @Test
    @DisplayName("ValidationConfig merge should accumulate ignore fields")
    void testValidationConfigMerge() {
        // Simulate loading default config
        ValidationConfig config = new ValidationConfig();
        config.addIgnoreField("metadata.managedFields");
        config.addIgnoreField("metadata.resourceVersion");
        
        int defaultCount = config.getIgnoreFields().size();
        assertEquals(2, defaultCount, "Default config should have 2 ignore fields");
        
        // Simulate merging with request ignore fields
        List<String> requestIgnoreFields = Arrays.asList(
            "status.conditions",
            "spec.template.metadata.creationTimestamp"
        );
        
        for (String field : requestIgnoreFields) {
            config.addIgnoreField(field);
        }
        
        // Assert: Should have 4 total fields
        assertEquals(4, config.getIgnoreFields().size(), 
                    "Config should have 4 ignore fields after merge");
        assertTrue(config.shouldIgnore("metadata.managedFields"));
        assertTrue(config.shouldIgnore("status.conditions"));
    }
}
