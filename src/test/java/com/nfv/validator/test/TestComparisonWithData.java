package com.nfv.validator.test;

import com.nfv.validator.comparison.NamespaceComparator;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.yaml.YamlDataCollector;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import com.nfv.validator.model.comparison.KeyComparison;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class TestComparisonWithData {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Testing Comparison with ConfigMap Data Fields ===\n");
        
        // Read YAML baseline
        String yamlContent = new String(Files.readAllBytes(
            Paths.get("test-configmap-comparison.yaml")));
        
        System.out.println("YAML Content:");
        System.out.println(yamlContent);
        System.out.println();
        
        // Parse YAML into baseline model
        YamlDataCollector collector = new YamlDataCollector();
        FlatNamespaceModel ns = 
            collector.collectFromYamlContent(yamlContent, "baseline");
        
        System.out.println("Baseline namespace: " + ns.getName());
        System.out.println("Objects in baseline: " + ns.getObjects().keySet());
        System.out.println();
        
        // Debug: print all object keys
        System.out.println("Looking for ConfigMap...");
        for (String key : ns.getObjects().keySet()) {
            FlatObjectModel obj = ns.getObjects().get(key);
            System.out.println("  Found: " + key + " (kind=" + obj.getKind() + ", name=" + obj.getName() + ")");
        }
        
        // Get the ConfigMap object - try different key formats
        FlatObjectModel configMap = ns.getObjects().get("test-config");
        if (configMap == null) {
            configMap = ns.getObjects().get("ConfigMap/test-config");
        }
        if (configMap == null) {
            // Try to find any ConfigMap
            for (Map.Entry<String, FlatObjectModel> entry : ns.getObjects().entrySet()) {
                if (entry.getValue().getKind().equals("ConfigMap")) {
                    configMap = entry.getValue();
                    System.out.println("Found ConfigMap with key: " + entry.getKey());
                    break;
                }
            }
        }
        
        if (configMap == null) {
            System.out.println("ERROR: ConfigMap not found!");
            return;
        }
        
        System.out.println("ConfigMap details:");
        System.out.println("  Kind: " + configMap.getKind());
        System.out.println("  Name: " + configMap.getName());
        
        System.out.println("\nAll fields from getAllFields():");
        Map<String, String> allFields = configMap.getAllFields();
        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        
        System.out.println("\nData fields count: " + 
            allFields.keySet().stream().filter(k -> k.startsWith("data.")).count());
        
        // Create a comparison
        System.out.println("\n=== Testing Comparison ===");
        
        Map<String, FlatObjectModel> leftMap = ns.getObjects();
        Map<String, FlatObjectModel> rightMap = ns.getObjects(); // Same for testing
        
        NamespaceComparison comparison = NamespaceComparator.compareNamespace(
            leftMap, rightMap, "baseline", "actual", null);
        
        System.out.println("Comparison results:");
        for (Map.Entry<String, ObjectComparison> entry : 
                comparison.getObjectComparisons().entrySet()) {
            ObjectComparison objComp = entry.getValue();
            System.out.println("\nObject: " + objComp.getObjectId());
            System.out.println("  Type: " + objComp.getObjectType());
            System.out.println("  Items count: " + objComp.getItems().size());
            
            long dataItemsCount = objComp.getItems().stream()
                .filter(item -> item.getKey().startsWith("data."))
                .count();
            System.out.println("  Data items count: " + dataItemsCount);
            
            if (dataItemsCount > 0) {
                System.out.println("\n  Data items:");
                for (KeyComparison item : objComp.getItems()) {
                    if (item.getKey().startsWith("data.")) {
                        System.out.println("    " + item.getKey() + ": " + 
                            item.getStatus() + " (left=" + item.getLeftValue() + 
                            ", right=" + item.getRightValue() + ")");
                    }
                }
            }
        }
        
        // Verify result
        ObjectComparison objComp = comparison.getObjectComparisons()
            .get("test-config"); // Use simple name key
        
        if (objComp == null) {
            System.out.println("\nERROR: Could not find comparison for test-config");
            System.out.println("Available keys: " + comparison.getObjectComparisons().keySet());
            return;
        }
        
        long dataItemsInComparison = objComp.getItems().stream()
            .filter(item -> item.getKey().startsWith("data."))
            .count();
        
        System.out.println("\n=== RESULT ===");
        if (dataItemsInComparison >= 3) {
            System.out.println("SUCCESS: Data fields are included in comparison!");
            System.out.println("Found " + dataItemsInComparison + " data items");
        } else {
            System.out.println("FAILURE: Data fields are missing from comparison!");
            System.out.println("Expected 3 or more, found " + dataItemsInComparison);
        }
    }
}
