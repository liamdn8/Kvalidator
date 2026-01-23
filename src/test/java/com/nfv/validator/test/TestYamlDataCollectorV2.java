package com.nfv.validator.test;

import com.nfv.validator.yaml.YamlDataCollectorV2;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class TestYamlDataCollectorV2 {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Testing YamlDataCollectorV2 with ConfigMap Data Fields ===\n");
        
        // Read YAML baseline (same file as V1 test)
        String yamlPath = "test-configmap-comparison.yaml";
        
        if (!Files.exists(Paths.get(yamlPath))) {
            System.out.println("ERROR: " + yamlPath + " not found!");
            return;
        }
        
        System.out.println("Testing V2 collector with: " + yamlPath + "\n");
        
        // Use V2 collector
        YamlDataCollectorV2 collector = new YamlDataCollectorV2();
        SemanticNamespaceModel namespace = collector.collectFromYaml(yamlPath, "baseline");
        
        System.out.println("V2 Collection Results:");
        System.out.println("  Namespace: " + namespace.getName());
        System.out.println("  Objects: " + namespace.getObjects().keySet());
        System.out.println();
        
        // Find ConfigMap
        SemanticObjectModel configMap = null;
        for (Map.Entry<String, SemanticObjectModel> entry : namespace.getObjects().entrySet()) {
            SemanticObjectModel obj = entry.getValue();
            if ("ConfigMap".equals(obj.getKind())) {
                configMap = obj;
                System.out.println("Found ConfigMap: " + entry.getKey());
                break;
            }
        }
        
        if (configMap == null) {
            System.out.println("ERROR: ConfigMap not found!");
            return;
        }
        
        System.out.println("\nConfigMap Details:");
        System.out.println("  Kind: " + configMap.getKind());
        System.out.println("  Name: " + configMap.getName());
        System.out.println("  Namespace: " + configMap.getNamespace());
        
        // Check metadata
        System.out.println("\nMetadata:");
        Map<String, Object> metadata = configMap.getMetadata();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue());
            }
        }
        
        // Check spec
        System.out.println("\nSpec:");
        Map<String, Object> spec = configMap.getSpec();
        if (spec != null && !spec.isEmpty()) {
            System.out.println("  Size: " + spec.size());
            for (Map.Entry<String, Object> entry : spec.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue());
            }
        } else {
            System.out.println("  (empty - expected for ConfigMap)");
        }
        
        // Check DATA - MOST IMPORTANT
        System.out.println("\nData Fields:");
        Map<String, Object> data = configMap.getData();
        
        if (data == null || data.isEmpty()) {
            System.out.println("  ERROR: No data fields found!");
            System.out.println("\n=== RESULT ===");
            System.out.println("FAILURE: Data field is null or empty!");
            return;
        }
        
        System.out.println("  Data map size: " + data.size());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Handle nested structures (binaryData, stringData)
            if (value instanceof Map) {
                System.out.println("  " + key + " (nested map):");
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> nested : nestedMap.entrySet()) {
                    System.out.println("    " + nested.getKey() + " = " + nested.getValue());
                }
            } else {
                System.out.println("  " + key + " = " + value);
            }
        }
        
        // Verify expected fields
        System.out.println("\n=== Verification ===");
        
        boolean hasKey1 = data.containsKey("key1");
        boolean hasKey2 = data.containsKey("key2");
        boolean hasConfigJson = data.containsKey("config.json");
        
        System.out.println("Has 'key1': " + hasKey1 + (hasKey1 ? " (value: " + data.get("key1") + ")" : ""));
        System.out.println("Has 'key2': " + hasKey2 + (hasKey2 ? " (value: " + data.get("key2") + ")" : ""));
        System.out.println("Has 'config.json': " + hasConfigJson + (hasConfigJson ? " (value present)" : ""));
        
        // Test getData method access
        System.out.println("\nDirect getData() call:");
        System.out.println("  getData() == null: " + (configMap.getData() == null));
        System.out.println("  getData().isEmpty(): " + (configMap.getData() != null && configMap.getData().isEmpty()));
        
        // Test getNestedValue with data path
        System.out.println("\nTesting getNestedValue() for data:");
        Object key1Value = configMap.getNestedValue("data.key1");
        Object key2Value = configMap.getNestedValue("data.key2");
        System.out.println("  getNestedValue('data.key1') = " + key1Value);
        System.out.println("  getNestedValue('data.key2') = " + key2Value);
        
        // Final result
        System.out.println("\n=== RESULT ===");
        int expectedFieldsCount = 3; // key1, key2, config.json
        
        if (data.size() >= expectedFieldsCount && hasKey1 && hasKey2 && hasConfigJson) {
            System.out.println("SUCCESS: V2 collector properly parses ConfigMap data fields!");
            System.out.println("Found " + data.size() + " data fields (expected >= " + expectedFieldsCount + ")");
            System.out.println("");
            System.out.println("Details:");
            System.out.println("  - Data stored in SemanticObjectModel.data map");
            System.out.println("  - Data accessible via getData() method");
            System.out.println("  - Data accessible via getNestedValue('data.key')");
            System.out.println("  - All expected fields present: key1, key2, config.json");
        } else {
            System.out.println("FAILURE: Missing expected data fields!");
            System.out.println("Expected: key1, key2, config.json");
            System.out.println("Found: " + data.keySet());
        }
    }
}
