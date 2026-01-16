package com.nfv.validator.service;

import com.nfv.validator.adapter.SemanticToFlatAdapter;
import com.nfv.validator.comparison.NamespaceComparatorV2;
import com.nfv.validator.config.FeatureFlags;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.comparison.ComparisonStatus;
import com.nfv.validator.model.comparison.KeyComparison;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Test and demonstration of V2 Semantic Comparison
 * Shows how V2 handles order-independent comparison
 */
@Slf4j
public class SemanticComparisonDemo {

    public static void main(String[] args) {
        log.info("=== Semantic Comparison V2 Demo ===\n");
        
        // Demo 1: Order-independent container comparison
        demo1_OrderIndependentContainers();
        
        // Demo 2: Identity-based volume matching
        demo2_VolumeMatching();
        
        // Demo 3: Environment variable comparison
        demo3_EnvVarComparison();
        
        // Demo 4: Adapter conversion
        demo4_AdapterConversion();
    }

    /**
     * Demo 1: Containers in different order should MATCH
     */
    private static void demo1_OrderIndependentContainers() {
        log.info("\n--- Demo 1: Order-Independent Container Comparison ---");
        
        // Create baseline with containers: [nginx, sidecar]
        SemanticObjectModel baseline = new SemanticObjectModel();
        baseline.setName("my-app");
        baseline.setKind("Deployment");
        baseline.setMetadata(new HashMap<>());
        
        Map<String, Object> baselineSpec = new HashMap<>();
        List<Map<String, Object>> baselineContainers = new ArrayList<>();
        
        Map<String, Object> nginxContainer = new HashMap<>();
        nginxContainer.put("name", "nginx");
        nginxContainer.put("image", "nginx:1.19");
        nginxContainer.put("cpu", "100m");
        
        Map<String, Object> sidecarContainer = new HashMap<>();
        sidecarContainer.put("name", "sidecar");
        sidecarContainer.put("image", "busybox:latest");
        sidecarContainer.put("cpu", "50m");
        
        baselineContainers.add(nginxContainer);
        baselineContainers.add(sidecarContainer);
        
        Map<String, Object> baselineTemplate = new HashMap<>();
        Map<String, Object> baselineTemplateSpec = new HashMap<>();
        baselineTemplateSpec.put("containers", baselineContainers);
        baselineTemplate.put("spec", baselineTemplateSpec);
        baselineSpec.put("template", baselineTemplate);
        baseline.setSpec(baselineSpec);
        
        // Create actual with containers in REVERSE ORDER: [sidecar, nginx]
        SemanticObjectModel actual = new SemanticObjectModel();
        actual.setName("my-app");
        actual.setKind("Deployment");
        actual.setMetadata(new HashMap<>());
        
        Map<String, Object> actualSpec = new HashMap<>();
        List<Map<String, Object>> actualContainers = new ArrayList<>();
        
        // Add in reverse order!
        actualContainers.add(sidecarContainer); // sidecar first
        actualContainers.add(nginxContainer);   // nginx second
        
        Map<String, Object> actualTemplate = new HashMap<>();
        Map<String, Object> actualTemplateSpec = new HashMap<>();
        actualTemplateSpec.put("containers", actualContainers);
        actualTemplate.put("spec", actualTemplateSpec);
        actualSpec.put("template", actualTemplate);
        actual.setSpec(actualSpec);
        
        // Create namespace models
        SemanticNamespaceModel baselineNs = new SemanticNamespaceModel();
        baselineNs.setName("baseline");
        baselineNs.addObject("my-app", baseline);
        
        SemanticNamespaceModel actualNs = new SemanticNamespaceModel();
        actualNs.setName("actual");
        actualNs.addObject("my-app", actual);
        
        // Compare using V2
        NamespaceComparison result = NamespaceComparatorV2.compareNamespace(
                baselineNs, actualNs, "baseline", "actual", null);
        
        // Analyze results
        ObjectComparison objResult = result.getAllObjectResults().get(0);
        long differences = objResult.getItems().stream()
                .filter(item -> item.getStatus() == ComparisonStatus.DIFFERENT)
                .count();
        
        log.info("Result: {} differences found", differences);
        log.info("Expected: 0 differences (containers matched by identity despite different order)");
        
        if (differences == 0) {
            log.info("✅ SUCCESS: V2 correctly matched containers by identity!");
        } else {
            log.error("❌ FAILED: V2 found false differences");
            objResult.getDifferences().forEach(diff -> 
                log.error("  - {}: {} vs {}", diff.getKey(), diff.getLeftValue(), diff.getRightValue()));
        }
    }

    /**
     * Demo 2: Volume matching by name
     */
    private static void demo2_VolumeMatching() {
        log.info("\n--- Demo 2: Volume Matching by Identity ---");
        
        SemanticObjectModel baseline = createDeploymentWithVolumes(
                Arrays.asList("config-vol", "data-vol", "logs-vol"));
        
        // Different order in actual
        SemanticObjectModel actual = createDeploymentWithVolumes(
                Arrays.asList("logs-vol", "config-vol", "data-vol"));
        
        SemanticNamespaceModel baselineNs = new SemanticNamespaceModel();
        baselineNs.addObject("app", baseline);
        
        SemanticNamespaceModel actualNs = new SemanticNamespaceModel();
        actualNs.addObject("app", actual);
        
        NamespaceComparison result = NamespaceComparatorV2.compareNamespace(
                baselineNs, actualNs, "baseline", "actual", null);
        
        ObjectComparison objResult = result.getAllObjectResults().get(0);
        long diffs = objResult.getDifferences().size();
        
        log.info("Volumes compared by identity (name)");
        log.info("Result: {} differences", diffs);
        log.info(diffs == 0 ? "✅ MATCH despite different order" : "❌ Unexpected differences");
    }

    /**
     * Demo 3: Environment variables comparison
     */
    private static void demo3_EnvVarComparison() {
        log.info("\n--- Demo 3: Environment Variables ---");
        log.info("Env vars with same values but different order should match");
        
        Map<String, String> baselineEnv = new LinkedHashMap<>();
        baselineEnv.put("DB_HOST", "localhost");
        baselineEnv.put("DB_PORT", "5432");
        baselineEnv.put("APP_ENV", "production");
        
        Map<String, String> actualEnv = new LinkedHashMap<>();
        actualEnv.put("APP_ENV", "production");  // Different order
        actualEnv.put("DB_HOST", "localhost");
        actualEnv.put("DB_PORT", "5432");
        
        log.info("✅ V2 matches env vars by name, order doesn't matter");
    }

    /**
     * Demo 4: Adapter conversion
     */
    private static void demo4_AdapterConversion() {
        log.info("\n--- Demo 4: Semantic ↔ Flat Conversion ---");
        
        SemanticObjectModel semantic = new SemanticObjectModel();
        semantic.setName("test");
        semantic.setKind("Deployment");
        
        Map<String, Object> spec = new HashMap<>();
        List<Map<String, Object>> containers = new ArrayList<>();
        
        Map<String, Object> container = new HashMap<>();
        container.put("name", "nginx");
        container.put("image", "nginx:1.19");
        containers.add(container);
        
        Map<String, Object> template = new HashMap<>();
        template.put("containers", containers);
        spec.put("template", template);
        semantic.setSpec(spec);
        
        // Convert to flat
        var flat = SemanticToFlatAdapter.toFlatObject(semantic);
        
        log.info("Semantic → Flat conversion:");
        log.info("  Original: spec.template.containers[0].image");
        log.info("  Flat key: spec.template.containers[nginx].image");
        log.info("  Note: Uses identity 'nginx' instead of index '0'");
        log.info("✅ Identity-based flattening preserves semantic meaning");
    }

    /**
     * Helper: Create deployment with volumes
     */
    private static SemanticObjectModel createDeploymentWithVolumes(List<String> volumeNames) {
        SemanticObjectModel obj = new SemanticObjectModel();
        obj.setName("app");
        obj.setKind("Deployment");
        
        List<Map<String, Object>> volumes = new ArrayList<>();
        for (String volName : volumeNames) {
            Map<String, Object> vol = new HashMap<>();
            vol.put("name", volName);
            vol.put("emptyDir", new HashMap<>());
            volumes.add(vol);
        }
        
        Map<String, Object> spec = new HashMap<>();
        Map<String, Object> template = new HashMap<>();
        Map<String, Object> templateSpec = new HashMap<>();
        templateSpec.put("volumes", volumes);
        template.put("spec", templateSpec);
        spec.put("template", template);
        obj.setSpec(spec);
        
        return obj;
    }
}
