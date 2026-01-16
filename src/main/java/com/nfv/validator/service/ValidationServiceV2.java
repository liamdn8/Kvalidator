package com.nfv.validator.service;

import com.nfv.validator.adapter.SemanticToFlatAdapter;
import com.nfv.validator.comparison.NamespaceComparatorV2;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollectorV2;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.yaml.YamlDataCollectorV2;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * V2 Validation Service - Uses semantic comparison engine
 * Provides same interface as V1 but uses order-independent semantic comparison
 * This service acts as a bridge between V2 engine and existing API
 */
@Slf4j
public class ValidationServiceV2 {

    /**
     * Compare two FlatNamespaceModel using V2 semantic comparison
     * Converts flat->semantic->compare->return as flat
     * This is for existing code that works with FlatNamespaceModel
     * 
     * @param left Left flat namespace model
     * @param right Right flat namespace model
     * @param leftName Left namespace display name
     * @param rightName Right namespace display name
     * @param config Validation config
     * @return NamespaceComparison result
     */
    public static NamespaceComparison compareFlat(
            FlatNamespaceModel left,
            FlatNamespaceModel right,
            String leftName,
            String rightName,
            ValidationConfig config) {
        
        log.info("[V2 Service] Comparing flat models: {} vs {}", leftName, rightName);
        
        // Convert flat to semantic
        SemanticNamespaceModel leftSemantic = SemanticToFlatAdapter.toSemanticModel(left);
        SemanticNamespaceModel rightSemantic = SemanticToFlatAdapter.toSemanticModel(right);
        
        // Compare using V2 comparator
        NamespaceComparison comparison = NamespaceComparatorV2.compareNamespace(
                leftSemantic, rightSemantic,
                leftName, rightName,
                config);
        
        log.info("[V2 Service] Flat comparison complete: {} objects compared", 
                comparison.getAllObjectResults().size());
        
        return comparison;
    }

    /**
     * Compare two namespaces using V2 semantic comparison
     * Returns result in flat format for API compatibility
     * 
     * @param leftClient Left cluster client
     * @param rightClient Right cluster client
     * @param leftNamespace Left namespace name
     * @param rightNamespace Right namespace name
     * @param leftCluster Left cluster name
     * @param rightCluster Right cluster name
     * @param config Validation config
     * @return NamespaceComparison in flat format (API compatible)
     */
    public static NamespaceComparison compareNamespacesV2(
            KubernetesClient leftClient,
            KubernetesClient rightClient,
            String leftNamespace,
            String rightNamespace,
            String leftCluster,
            String rightCluster,
            ValidationConfig config) {
        
        log.info("[V2 Service] Comparing namespaces: {}@{} vs {}@{}", 
                leftNamespace, leftCluster, rightNamespace, rightCluster);
        
        // Collect data using V2 collectors (semantic structure)
        K8sDataCollectorV2 leftCollector = new K8sDataCollectorV2(leftClient);
        K8sDataCollectorV2 rightCollector = new K8sDataCollectorV2(rightClient);
        
        SemanticNamespaceModel leftSemantic = leftCollector.collectNamespace(leftNamespace, leftCluster);
        SemanticNamespaceModel rightSemantic = rightCollector.collectNamespace(rightNamespace, rightCluster);
        
        // Compare using V2 comparator (semantic comparison)
        NamespaceComparison comparison = NamespaceComparatorV2.compareNamespace(
                leftSemantic, rightSemantic,
                leftNamespace + "@" + leftCluster,
                rightNamespace + "@" + rightCluster,
                config);
        
        log.info("[V2 Service] Comparison complete: {} objects compared", 
                comparison.getAllObjectResults().size());
        
        return comparison;
    }

    /**
     * Compare baseline YAML with runtime namespace using V2
     * 
     * @param baselinePath Path to baseline YAML
     * @param baselineName Baseline name
     * @param runtimeClient Runtime cluster client
     * @param runtimeNamespace Runtime namespace
     * @param runtimeCluster Runtime cluster name
     * @param config Validation config
     * @return NamespaceComparison result
     */
    public static NamespaceComparison compareBaselineWithRuntimeV2(
            String baselinePath,
            String baselineName,
            KubernetesClient runtimeClient,
            String runtimeNamespace,
            String runtimeCluster,
            ValidationConfig config) throws IOException {
        
        log.info("[V2 Service] Comparing baseline '{}' with runtime {}@{}", 
                baselineName, runtimeNamespace, runtimeCluster);
        
        // Collect baseline from YAML using V2
        YamlDataCollectorV2 yamlCollector = new YamlDataCollectorV2();
        SemanticNamespaceModel baselineSemantic = yamlCollector.collectFromYaml(baselinePath, baselineName);
        
        // Collect runtime data using V2
        K8sDataCollectorV2 k8sCollector = new K8sDataCollectorV2(runtimeClient);
        SemanticNamespaceModel runtimeSemantic = k8sCollector.collectNamespace(runtimeNamespace, runtimeCluster);
        
        // Compare using V2 comparator
        NamespaceComparison comparison = NamespaceComparatorV2.compareNamespace(
                baselineSemantic, runtimeSemantic,
                baselineName + " (Baseline)",
                runtimeNamespace + "@" + runtimeCluster + " (Runtime)",
                config);
        
        log.info("[V2 Service] Baseline comparison complete: {} objects compared", 
                comparison.getAllObjectResults().size());
        
        return comparison;
    }

    /**
     * Compare multiple baselines with runtime (CNF checklist mode)
     * 
     * @param baselinePaths Map of baseline name -> path
     * @param runtimeClient Runtime cluster client
     * @param runtimeNamespace Runtime namespace
     * @param runtimeCluster Runtime cluster name
     * @param config Validation config
     * @return Map of baseline name -> comparison result
     */
    public static Map<String, NamespaceComparison> compareMultipleBaselinesV2(
            Map<String, String> baselinePaths,
            KubernetesClient runtimeClient,
            String runtimeNamespace,
            String runtimeCluster,
            ValidationConfig config) {
        
        log.info("[V2 Service] Comparing {} baselines with runtime {}@{}", 
                baselinePaths.size(), runtimeNamespace, runtimeCluster);
        
        Map<String, NamespaceComparison> results = new HashMap<>();
        
        // Collect runtime once (reuse for all baselines)
        K8sDataCollectorV2 k8sCollector = new K8sDataCollectorV2(runtimeClient);
        SemanticNamespaceModel runtimeSemantic = k8sCollector.collectNamespace(runtimeNamespace, runtimeCluster);
        
        YamlDataCollectorV2 yamlCollector = new YamlDataCollectorV2();
        
        // Compare each baseline
        for (Map.Entry<String, String> entry : baselinePaths.entrySet()) {
            String baselineName = entry.getKey();
            String baselinePath = entry.getValue();
            
            try {
                SemanticNamespaceModel baselineSemantic = yamlCollector.collectFromYaml(baselinePath, baselineName);
                
                NamespaceComparison comparison = NamespaceComparatorV2.compareNamespace(
                        baselineSemantic, runtimeSemantic,
                        baselineName + " (Baseline)",
                        runtimeNamespace + "@" + runtimeCluster + " (Runtime)",
                        config);
                
                results.put(baselineName, comparison);
                
            } catch (Exception e) {
                log.error("[V2 Service] Failed to compare baseline '{}': {}", baselineName, e.getMessage());
            }
        }
        
        log.info("[V2 Service] Completed {} baseline comparisons", results.size());
        
        return results;
    }

    /**
     * Convert semantic namespace to flat for backward compatibility
     * Used when need to return flat format to existing API consumers
     */
    public static FlatNamespaceModel toFlatModel(SemanticNamespaceModel semantic) {
        return SemanticToFlatAdapter.toFlatModel(semantic);
    }

    /**
     * Convert flat namespace to semantic for V2 processing
     * Used when receiving flat data from API and need to use V2 engine
     */
    public static SemanticNamespaceModel toSemanticModel(FlatNamespaceModel flat) {
        return SemanticToFlatAdapter.toSemanticModel(flat);
    }
}
