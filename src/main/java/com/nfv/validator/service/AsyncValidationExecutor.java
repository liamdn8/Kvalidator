package com.nfv.validator.service;

import com.nfv.validator.comparison.NamespaceComparator;
import com.nfv.validator.config.ConfigLoader;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollector;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.api.*;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.report.ExcelReportGenerator;
import com.nfv.validator.yaml.YamlDataCollector;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Asynchronous executor for validation jobs
 * Runs validation tasks in background threads
 */
@Slf4j
@ApplicationScoped
public class AsyncValidationExecutor {
    
    private final KubernetesClusterManager clusterManager;
    private final ConfigLoader configLoader;
    
    @Inject
    ValidationJobService jobService;
    
    @Inject
    JsonResultExporter jsonExporter;
    
    public AsyncValidationExecutor() {
        // Direct initialization without thread pool for now
        this.clusterManager = new KubernetesClusterManager();
        this.configLoader = new ConfigLoader();
    }
    
    /**
     * Execute validation job asynchronously
     */
    public void executeAsync(String jobId, ValidationJobRequest request) {
        log.info("Submitting job {} for execution", jobId);
        
        // Run in a new thread to avoid queue blocking
        Thread thread = new Thread(() -> {
            log.info("Thread started for job {}", jobId);
            try {
                executeValidation(jobId, request);
            } catch (Throwable e) {
                log.error("Failed to execute validation job {}", jobId, e);
                try {
                    jobService.failJob(jobId, "Execution failed: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to update job status to failed", ex);
                }
            }
        });
        
        thread.setName("validation-job-" + jobId);
        thread.setDaemon(true);
        thread.start();
        log.info("Job {} thread started", jobId);
    }
    
    /**
     * Execute the actual validation logic
     */
    private void executeValidation(String jobId, ValidationJobRequest request) throws Exception {
        log.info("Starting validation execution for job {}", jobId);
        
        // Mark job as processing
        jobService.startJob(jobId);
        
        // Load validation config
        ValidationConfig validationConfig = configLoader.load(request.getConfigFile());
        
        // Update progress
        updateProgress(jobId, "Loading configuration", 5, 0, request.getNamespaces().size(), 0);
        
        // Load baseline if specified
        FlatNamespaceModel baselineModel = null;
        if (request.getBaselineObjects() != null && !request.getBaselineObjects().isEmpty()) {
            // Client-side preprocessed baseline
            updateProgress(jobId, "Loading baseline data", 10, 0, request.getNamespaces().size(), 0);
            
            baselineModel = new FlatNamespaceModel();
            baselineModel.setName(request.getBaselineNamespace() != null ? request.getBaselineNamespace() : "baseline");
            baselineModel.setClusterName(request.getBaselineCluster() != null ? request.getBaselineCluster() : "baseline");
            baselineModel.setObjects(new HashMap<>());
            
            for (Map.Entry<String, Map<String, String>> entry : request.getBaselineObjects().entrySet()) {
                String objectName = entry.getKey();
                Map<String, String> flattenedFields = entry.getValue();
                
                FlatObjectModel obj = new FlatObjectModel();
                obj.setName(objectName);
                obj.setMetadata(new HashMap<>());
                obj.setSpec(new HashMap<>());
                
                for (Map.Entry<String, String> field : flattenedFields.entrySet()) {
                    String fieldPath = field.getKey();
                    String value = field.getValue();
                    
                    if (fieldPath.startsWith("kind")) {
                        obj.setKind(value);
                    } else if (fieldPath.startsWith("apiVersion")) {
                        obj.setApiVersion(value);
                    } else if (fieldPath.startsWith("metadata.namespace")) {
                        obj.setNamespace(value);
                    } else if (fieldPath.startsWith("metadata.")) {
                        obj.addMetadata(fieldPath.substring(9), value);
                    } else if (fieldPath.startsWith("spec.")) {
                        obj.addSpec(fieldPath.substring(5), value);
                    }
                }
                
                baselineModel.addObject(objectName, obj);
            }
            
            log.info("Loaded {} objects from client-side baseline", baselineModel.getObjects().size());
        } else if (request.getBaselinePath() != null) {
            // Server-side baseline file
            updateProgress(jobId, "Loading baseline YAML", 10, 0, request.getNamespaces().size(), 0);
            
            YamlDataCollector yamlCollector = new YamlDataCollector();
            baselineModel = yamlCollector.collectFromYaml(request.getBaselinePath(), "baseline");
            
            log.info("Loaded {} objects from baseline file", baselineModel.getObjects().size());
        }
        
        // Parse namespace targets
        List<NamespaceTarget> targets = new ArrayList<>();
        String defaultCluster = request.getCluster() != null ? request.getCluster() : "current";
        
        for (String nsArg : request.getNamespaces()) {
            targets.add(parseNamespaceArg(nsArg, defaultCluster));
        }
        
        // Collect data from all namespaces
        updateProgress(jobId, "Collecting namespace data", 20, 0, targets.size(), 0);
        
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        
        if (baselineModel != null) {
            namespaceModels.add(baselineModel);
        }
        
        int objectsCollected = 0;
        for (int i = 0; i < targets.size(); i++) {
            NamespaceTarget target = targets.get(i);
            
            updateProgress(jobId, "Collecting: " + target.namespaceName, 
                    20 + (40 * i / targets.size()), i, targets.size(), objectsCollected);
            
            KubernetesClient client = clusterManager.getClient(target.clusterName);
            K8sDataCollector collector = new K8sDataCollector(client);
            
            FlatNamespaceModel model;
            if (request.getKinds() != null && !request.getKinds().isEmpty()) {
                model = collector.collectNamespaceByKinds(
                        target.namespaceName, target.clusterName, request.getKinds());
            } else {
                model = collector.collectNamespace(target.namespaceName, target.clusterName);
            }
            
            namespaceModels.add(model);
            objectsCollected += model.getObjects().size();
            
            log.info("Collected {} objects from {}/{}", 
                    model.getObjects().size(), target.clusterName, target.namespaceName);
        }
        
        // Perform comparisons
        updateProgress(jobId, "Comparing namespaces", 60, targets.size(), targets.size(), objectsCollected);
        
        Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();
        int totalDifferences = 0;
        
        if (baselineModel != null) {
            // Baseline mode
            for (int i = 1; i < namespaceModels.size(); i++) {
                FlatNamespaceModel ns = namespaceModels.get(i);
                
                String comparisonKey = buildComparisonKey(baselineModel, ns);
                NamespaceComparison comparison = NamespaceComparator.compareNamespace(
                        baselineModel.getObjects(), ns.getObjects(),
                        baselineModel.getName(), ns.getName(),
                        validationConfig);
                
                comparisons.put(comparisonKey, comparison);
                totalDifferences += comparison.getSummary().getDifferencesCount();
            }
        } else {
            // Pairwise comparison
            for (int i = 0; i < namespaceModels.size() - 1; i++) {
                for (int j = i + 1; j < namespaceModels.size(); j++) {
                    FlatNamespaceModel ns1 = namespaceModels.get(i);
                    FlatNamespaceModel ns2 = namespaceModels.get(j);
                    
                    String comparisonKey = buildComparisonKey(ns1, ns2);
                    NamespaceComparison comparison = NamespaceComparator.compareNamespace(
                            ns1.getObjects(), ns2.getObjects(),
                            ns1.getName(), ns2.getName(),
                            validationConfig);
                    
                    comparisons.put(comparisonKey, comparison);
                    totalDifferences += comparison.getSummary().getDifferencesCount();
                }
            }
        }
        
        // Create results directory
        Path resultsDir = jobService.createJobResultsDirectory(jobId);
        
        // Export to JSON
        updateProgress(jobId, "Generating JSON results", 80, targets.size(), targets.size(), objectsCollected);
        
        File jsonFile = resultsDir.resolve("validation-results.json").toFile();
        jsonExporter.exportToJson(jobId, comparisons, request.getDescription(), jsonFile);
        
        log.info("Exported JSON results to {}", jsonFile.getAbsolutePath());
        
        // Export to Excel if requested
        if (request.getExportExcel() == null || request.getExportExcel()) {
            updateProgress(jobId, "Generating Excel report", 90, targets.size(), targets.size(), objectsCollected);
            
            File excelFile = resultsDir.resolve("validation-report.xlsx").toFile();
            ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
            excelGenerator.generateReport(namespaceModels, comparisons, excelFile.getAbsolutePath(), validationConfig);
            
            log.info("Exported Excel report to {}", excelFile.getAbsolutePath());
        }
        
        // Mark job as completed
        jobService.completeJob(jobId, resultsDir.toString(), objectsCollected, totalDifferences);
        
        log.info("Validation job {} completed successfully", jobId);
    }
    
    /**
     * Update job progress
     */
    private void updateProgress(String jobId, String step, int percentage, 
                                int namespacesProcessed, int totalNamespaces, 
                                int objectsCompared) {
        JobProgress progress = JobProgress.builder()
                .currentStep(step)
                .percentage(percentage)
                .namespacesProcessed(namespacesProcessed)
                .totalNamespaces(totalNamespaces)
                .objectsCompared(objectsCompared)
                .build();
        
        jobService.updateProgress(jobId, progress);
    }
    
    /**
     * Parse namespace argument (cluster/namespace or just namespace)
     */
    private NamespaceTarget parseNamespaceArg(String arg, String defaultCluster) {
        String[] parts = arg.split("/");
        
        if (parts.length == 2) {
            return new NamespaceTarget(parts[0], parts[1]);
        } else {
            return new NamespaceTarget(defaultCluster, arg);
        }
    }

    private String buildComparisonKey(FlatNamespaceModel left, FlatNamespaceModel right) {
        return left.getClusterName() + "/" + left.getName() +
                "_vs_" + right.getClusterName() + "/" + right.getName();
    }
    
    /**
     * Inner class for namespace target
     */
    private static class NamespaceTarget {
        String clusterName;
        String namespaceName;
        
        NamespaceTarget(String clusterName, String namespaceName) {
            this.clusterName = clusterName;
            this.namespaceName = namespaceName;
        }
    }
}
