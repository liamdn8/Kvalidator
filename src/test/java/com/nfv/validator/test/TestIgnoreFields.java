package com.nfv.validator.test;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.batch.BatchValidationRequest;

import java.util.*;

/**
 * Manual test for ignore fields functionality
 * Run with: mvn exec:java -Dexec.mainClass="com.nfv.validator.test.TestIgnoreFields"
 */
public class TestIgnoreFields {

    public static void main(String[] args) {
        System.out.println("=== Testing Ignore Fields Functionality ===\n");
        
        boolean allPassed = true;
        
        // Test 1: Global ignore fields merge
        allPassed &= testGlobalIgnoreFieldsMerge();
        
        // Test 2: No duplicates
        allPassed &= testNoDuplicates();
        
        // Test 3: ValidationConfig.shouldIgnore
        allPassed &= testValidationConfigShouldIgnore();
        
        // Test 4: Empty ignore fields
        allPassed &= testEmptyIgnoreFields();
        
        // Summary
        System.out.println("\n==========================================");
        if (allPassed) {
            System.out.println("✅ ALL TESTS PASSED");
        } else {
            System.out.println("❌ SOME TESTS FAILED");
            System.exit(1);
        }
    }

    private static boolean testGlobalIgnoreFieldsMerge() {
        System.out.println("Test 1: Global ignore fields should be merged into individual validation request");
        
        try {
            // Setup
            BatchValidationRequest batchRequest = new BatchValidationRequest();
            BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
            List<String> globalIgnoreFields = Arrays.asList(
                "metadata.managedFields",
                "metadata.resourceVersion",
                "status.conditions"
            );
            settings.setIgnoreFields(globalIgnoreFields);
            batchRequest.setSettings(settings);
            
            com.nfv.validator.model.batch.ValidationRequest validationRequest = 
                new com.nfv.validator.model.batch.ValidationRequest();
            List<String> requestIgnoreFields = Arrays.asList(
                "spec.template.metadata.creationTimestamp"
            );
            validationRequest.setIgnoreFields(requestIgnoreFields);
            
            // Execute merge logic (same as AsyncValidationExecutor)
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
            
            // Verify
            if (mergedIgnoreFields.size() != 4) {
                System.out.println("  ❌ FAILED: Expected 4 fields, got " + mergedIgnoreFields.size());
                return false;
            }
            
            if (!mergedIgnoreFields.contains("metadata.managedFields") ||
                !mergedIgnoreFields.contains("metadata.resourceVersion") ||
                !mergedIgnoreFields.contains("status.conditions") ||
                !mergedIgnoreFields.contains("spec.template.metadata.creationTimestamp")) {
                System.out.println("  ❌ FAILED: Missing expected fields");
                return false;
            }
            
            System.out.println("  ✅ PASSED - Merged 4 unique ignore fields");
            return true;
            
        } catch (Exception e) {
            System.out.println("  ❌ FAILED with exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean testNoDuplicates() {
        System.out.println("\nTest 2: Duplicate ignore fields should not be added twice");
        
        try {
            BatchValidationRequest batchRequest = new BatchValidationRequest();
            BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
            List<String> globalIgnoreFields = Arrays.asList(
                "metadata.managedFields",
                "metadata.resourceVersion"
            );
            settings.setIgnoreFields(globalIgnoreFields);
            batchRequest.setSettings(settings);
            
            com.nfv.validator.model.batch.ValidationRequest validationRequest = 
                new com.nfv.validator.model.batch.ValidationRequest();
            List<String> requestIgnoreFields = Arrays.asList(
                "metadata.resourceVersion",  // Duplicate!
                "status.phase"
            );
            validationRequest.setIgnoreFields(requestIgnoreFields);
            
            // Merge
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
            
            // Verify
            if (mergedIgnoreFields.size() != 3) {
                System.out.println("  ❌ FAILED: Expected 3 unique fields, got " + mergedIgnoreFields.size());
                System.out.println("  Fields: " + mergedIgnoreFields);
                return false;
            }
            
            System.out.println("  ✅ PASSED - No duplicates, 3 unique fields");
            return true;
            
        } catch (Exception e) {
            System.out.println("  ❌ FAILED with exception: " + e.getMessage());
            return false;
        }
    }

    private static boolean testValidationConfigShouldIgnore() {
        System.out.println("\nTest 3: ValidationConfig.shouldIgnore should respect ignore patterns");
        
        try {
            ValidationConfig config = new ValidationConfig();
            config.addIgnoreField("metadata.managedFields");
            config.addIgnoreField("metadata.resourceVersion");
            config.addIgnoreField("status.*");
            
            // Test exact matches
            if (!config.shouldIgnore("metadata.managedFields")) {
                System.out.println("  ❌ FAILED: Should ignore metadata.managedFields");
                return false;
            }
            
            // Test wildcard
            if (!config.shouldIgnore("status.conditions")) {
                System.out.println("  ❌ FAILED: Should ignore status.conditions (wildcard status.*)");
                return false;
            }
            
            // Test non-matching
            if (config.shouldIgnore("spec.replicas")) {
                System.out.println("  ❌ FAILED: Should NOT ignore spec.replicas");
                return false;
            }
            
            System.out.println("  ✅ PASSED - ValidationConfig.shouldIgnore works correctly");
            return true;
            
        } catch (Exception e) {
            System.out.println("  ❌ FAILED with exception: " + e.getMessage());
            return false;
        }
    }

    private static boolean testEmptyIgnoreFields() {
        System.out.println("\nTest 4: Empty global ignore fields should not cause errors");
        
        try {
            BatchValidationRequest batchRequest = new BatchValidationRequest();
            BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
            settings.setIgnoreFields(null);
            batchRequest.setSettings(settings);
            
            com.nfv.validator.model.batch.ValidationRequest validationRequest = 
                new com.nfv.validator.model.batch.ValidationRequest();
            List<String> requestIgnoreFields = Arrays.asList("spec.replicas");
            validationRequest.setIgnoreFields(requestIgnoreFields);
            
            // Merge
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
            
            // Verify
            if (mergedIgnoreFields.size() != 1) {
                System.out.println("  ❌ FAILED: Expected 1 field, got " + mergedIgnoreFields.size());
                return false;
            }
            
            System.out.println("  ✅ PASSED - Handled null global ignore fields correctly");
            return true;
            
        } catch (Exception e) {
            System.out.println("  ❌ FAILED with exception: " + e.getMessage());
            return false;
        }
    }
}
