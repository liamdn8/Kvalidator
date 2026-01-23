package com.nfv.validator.test;

import com.nfv.validator.comparison.NamespaceComparatorV2;
import com.nfv.validator.yaml.YamlDataCollectorV2;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import com.nfv.validator.model.comparison.KeyComparison;

import java.io.IOException;

public class TestComparisonV2WithData {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Testing V2 Comparison with ConfigMap Data Fields ===\n");
        
        String yamlPath = "test-configmap-comparison.yaml";
        
        // Collect using V2
        YamlDataCollectorV2 collector = new YamlDataCollectorV2();
        SemanticNamespaceModel baseline = collector.collectFromYaml(yamlPath, "baseline");
        
        System.out.println("Collected objects: " + baseline.getObjects().keySet());
        
        // Get ConfigMap
        SemanticObjectModel configMap = null;
        for (SemanticObjectModel obj : baseline.getObjects().values()) {
            if ("ConfigMap".equals(obj.getKind())) {
                configMap = obj;
                break;
            }
        }
        
        if (configMap == null) {
            System.out.println("ERROR: ConfigMap not found!");
            return;
        }
        
        System.out.println("\nConfigMap has data: " + (configMap.getData() != null));
        if (configMap.getData() != null) {
            System.out.println("Data fields: " + configMap.getData().keySet());
        }
        
        // Compare with itself (should be MATCH)
        System.out.println("\n=== Performing V2 Comparison ===");
        
        NamespaceComparison comparison = NamespaceComparatorV2.compareNamespace(
            baseline, baseline, "baseline", "actual", null);
        
        System.out.println("Comparison complete");
        System.out.println("Object comparisons: " + comparison.getObjectComparisons().size());
        
        // Find ConfigMap comparison
        ObjectComparison objComp = null;
        for (ObjectComparison oc : comparison.getObjectComparisons().values()) {
            if ("ConfigMap".equals(oc.getObjectType())) {
                objComp = oc;
                break;
            }
        }
        
        if (objComp == null) {
            System.out.println("ERROR: ConfigMap comparison not found!");
            return;
        }
        
        System.out.println("\nConfigMap Comparison:");
        System.out.println("  Object: " + objComp.getObjectId());
        System.out.println("  Type: " + objComp.getObjectType());
        System.out.println("  Total items: " + objComp.getItems().size());
        
        // Count data items
        long dataItems = objComp.getItems().stream()
            .filter(item -> item.getKey().startsWith("data."))
            .count();
        
        System.out.println("  Data items: " + dataItems);
        
        if (dataItems > 0) {
            System.out.println("\n  Data comparison items:");
            for (KeyComparison item : objComp.getItems()) {
                if (item.getKey().startsWith("data.")) {
                    System.out.println("    " + item.getKey() + 
                        " -> " + item.getStatus() +
                        " (left=" + truncate(item.getLeftValue(), 30) +
                        ", right=" + truncate(item.getRightValue(), 30) + ")");
                }
            }
        }
        
        // Verify
        System.out.println("\n=== RESULT ===");
        if (dataItems >= 3) {
            System.out.println("SUCCESS: V2 comparator includes ConfigMap data fields!");
            System.out.println("Found " + dataItems + " data items in comparison");
            System.out.println("");
            System.out.println("V2 Flow:");
            System.out.println("  1. YamlDataCollectorV2 parses YAML -> SemanticObjectModel");
            System.out.println("  2. SemanticObjectModel.data contains ConfigMap data");
            System.out.println("  3. NamespaceComparatorV2.compareObjects() calls:");
            System.out.println("     - compareNestedStructure('metadata', ...)");
            System.out.println("     - compareNestedStructure('spec', ...)");
            System.out.println("     - compareNestedStructure('data', ...) <- NOW INCLUDED!");
            System.out.println("  4. ObjectComparison.items contains data.* KeyComparisons");
            System.out.println("  5. JSON serialization -> Frontend");
        } else {
            System.out.println("FAILURE: Data fields missing from V2 comparison!");
            System.out.println("Expected >= 3 data items, found: " + dataItems);
        }
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
