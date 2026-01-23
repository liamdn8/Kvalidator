package com.nfv.validator.test;

import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.yaml.YamlDataCollector;
import com.nfv.validator.model.FlatNamespaceModel;

import java.util.Map;

/**
 * Simple test to verify ConfigMap data field is parsed and included
 */
public class TestConfigMapDataParsing {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Testing ConfigMap Data Field Parsing");
        System.out.println("========================================\n");
        
        // Create YAML content with ConfigMap
        String yamlContent = "apiVersion: v1\n" +
            "kind: ConfigMap\n" +
            "metadata:\n" +
            "  name: test-config\n" +
            "  labels:\n" +
            "    app: test\n" +
            "data:\n" +
            "  application.yaml: |\n" +
            "    server:\n" +
            "      port: 8080\n" +
            "  config.json: |\n" +
            "    {\"key\": \"value\"}\n" +
            "  simple-value: \"production\"\n" +
            "binaryData:\n" +
            "  logo.png: \"base64data\"\n";
        
        try {
            YamlDataCollector collector = new YamlDataCollector();
            FlatNamespaceModel namespace = collector.collectFromYamlContent(yamlContent, "test-baseline");
            
            System.out.println("✓ Parsed " + namespace.getObjects().size() + " objects\n");
            
            if (namespace.getObjects().isEmpty()) {
                System.err.println("❌ ERROR: No objects parsed!");
                return;
            }
            
            FlatObjectModel configMap = namespace.getObjects().get("test-config");
            
            if (configMap == null) {
                System.err.println("❌ ERROR: ConfigMap 'test-config' not found!");
                return;
            }
            
            System.out.println("ConfigMap: " + configMap.getName());
            System.out.println("  Kind: " + configMap.getKind());
            System.out.println("  Metadata fields: " + (configMap.getMetadata() != null ? configMap.getMetadata().size() : 0));
            System.out.println("  Spec fields: " + (configMap.getSpec() != null ? configMap.getSpec().size() : 0));
            System.out.println("  Data fields: " + (configMap.getData() != null ? configMap.getData().size() : 0));
            
            System.out.println("\n--- Metadata Map ---");
            if (configMap.getMetadata() != null) {
                configMap.getMetadata().forEach((k, v) -> 
                    System.out.println("  " + k + " = " + v)
                );
            }
            
            System.out.println("\n--- Spec Map ---");
            if (configMap.getSpec() != null) {
                configMap.getSpec().forEach((k, v) -> 
                    System.out.println("  " + k + " = " + v)
                );
            }
            
            System.out.println("\n--- Data Map ---");
            if (configMap.getData() != null) {
                configMap.getData().forEach((k, v) -> 
                    System.out.println("  " + k + " = " + (v.length() > 50 ? v.substring(0, 50) + "..." : v))
                );
            } else {
                System.err.println("❌ ERROR: Data map is NULL!");
            }
            
            System.out.println("\n--- getAllFields() Result ---");
            Map<String, String> allFields = configMap.getAllFields();
            System.out.println("Total fields: " + allFields.size());
            
            // Check for data fields
            long dataFieldCount = allFields.keySet().stream()
                .filter(k -> k.startsWith("data."))
                .count();
            
            System.out.println("Data fields in getAllFields(): " + dataFieldCount);
            
            if (dataFieldCount == 0) {
                System.err.println("\n❌ PROBLEM: No 'data.*' fields found in getAllFields()!");
                System.err.println("This means data fields won't appear in JSON output!");
            } else {
                System.out.println("\n✓ SUCCESS: Data fields are included in getAllFields()");
                System.out.println("\nData fields:");
                allFields.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("data."))
                    .forEach(e -> System.out.println("  " + e.getKey() + " = " + 
                        (e.getValue().length() > 50 ? e.getValue().substring(0, 50) + "..." : e.getValue())));
            }
            
            // Check for wrong spec.data fields
            long wrongDataFieldCount = allFields.keySet().stream()
                .filter(k -> k.startsWith("spec.data."))
                .count();
            
            if (wrongDataFieldCount > 0) {
                System.err.println("\n❌ ERROR: Found 'spec.data.*' fields (WRONG!)");
                System.err.println("Data should be 'data.*' not 'spec.data.*'");
            }
            
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
