package com.nfv.validator.service;

import com.nfv.validator.adapter.SemanticToFlatAdapter;
import com.nfv.validator.batch.BatchExecutor;
import com.nfv.validator.batch.BatchSummaryReportGenerator;
import com.nfv.validator.comparison.NamespaceComparator;
import com.nfv.validator.config.ConfigLoader;
import com.nfv.validator.config.FeatureFlags;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollector;
import com.nfv.validator.kubernetes.K8sDataCollectorV2;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.api.*;
import com.nfv.validator.model.batch.BatchExecutionResult;
import com.nfv.validator.model.batch.BatchValidationRequest;
import com.nfv.validator.model.cnf.CNFChecklistRequest;
import com.nfv.validator.model.comparison.CnfComparison;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.report.CNFChecklistExcelGenerator;
import com.nfv.validator.report.ExcelReportGenerator;
import com.nfv.validator.service.ValidationServiceV2;
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
    
    @Inject
    CNFChecklistService cnfChecklistService;
    
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
     * Execute batch validation job asynchronously
     */
    public void executeBatchAsync(String jobId, BatchValidationRequest request) {
        log.info("Submitting batch job {} for execution", jobId);
        
        // Run in a new thread
        Thread thread = new Thread(() -> {
            log.info("Thread started for batch job {}", jobId);
            try {
                executeBatchValidation(jobId, request);
            } catch (Throwable e) {
                log.error("Failed to execute batch job {}", jobId, e);
                try {
                    jobService.failJob(jobId, "Execution failed: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to update job status to failed", ex);
                }
            }
        });
        
        thread.setName("batch-job-" + jobId);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Execute CNF checklist validation job asynchronously
     */
    public void executeCNFChecklistAsync(String jobId, ValidationJobRequest request, 
                                         Map<String, FlatNamespaceModel> baselineMap) {
        log.info("Submitting CNF checklist job {} for execution", jobId);
        
        // Run in a new thread
        Thread thread = new Thread(() -> {
            log.info("Thread started for CNF checklist job {}", jobId);
            try {
                executeCNFChecklistValidation(jobId, request, baselineMap);
            } catch (Throwable e) {
                log.error("Failed to execute CNF checklist job {}", jobId, e);
                try {
                    jobService.failJob(jobId, "Execution failed: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to update job status to failed", ex);
                }
            }
        });
        
        thread.setName("cnf-checklist-job-" + jobId);
        thread.setDaemon(true);
        thread.start();
    }

    private void executeBatchValidation(String batchJobId, BatchValidationRequest batchRequest) throws Exception {
        log.info("Starting batch execution for job {}", batchJobId);
        
        jobService.startJob(batchJobId);
        jobService.updateProgress(batchJobId, JobProgress.builder()
                .currentStep("Initializing batch execution")
                .percentage(0)
                .build());

        int totalRequests = batchRequest.getRequests().size();
        int completedRequests = 0;
        int successfulRequests = 0;
        int failedRequests = 0;
        
        // Collect data for batch summary report
        Map<String, BatchSummaryReportGenerator.RequestExecutionData> batchExecutionData = new LinkedHashMap<>();
        List<BatchExecutionResult.RequestResult> requestResults = new ArrayList<>();
        
        // Create and execute individual validation jobs
        for (int i = 0; i < totalRequests; i++) {
            com.nfv.validator.model.batch.ValidationRequest batchValidationRequest = batchRequest.getRequests().get(i);
            String individualJobId = batchJobId + "-" + (i + 1);
            
            try {
                // Update batch job progress
                jobService.updateProgress(batchJobId, JobProgress.builder()
                        .currentStep("Executing validation " + (i + 1) + "/" + totalRequests + ": " + batchValidationRequest.getName())
                        .percentage((int) ((double) i / totalRequests * 100))
                        .build());
                
                // Convert batch ValidationRequest to ValidationJobRequest
                // Pass the entire batch request to merge global settings
                ValidationJobRequest jobRequest = convertBatchRequestToJobRequest(batchValidationRequest, batchRequest);
                
                // Preserve CNF checklist request from batch
                log.info("DEBUG: batchRequest.getCnfChecklistRequest() = {}", batchRequest.getCnfChecklistRequest());
                if (batchRequest.getCnfChecklistRequest() != null) {
                    jobRequest.setCnfChecklistRequest(batchRequest.getCnfChecklistRequest());
                    log.info("Set CNF checklist request in job: {}", jobRequest.getCnfChecklistRequest().getClass().getSimpleName());
                } else {
                    log.warn("CNF checklist request is NULL in batchRequest!");
                }
                
                // Create individual job using our batch-based ID scheme
                ValidationJobResponse individualJob = jobService.createJobWithId(individualJobId, jobRequest);
                
                // Add individual job to batch job
                jobService.addIndividualJobToBatch(batchJobId, individualJobId);
                
                // Set validation name in job
                individualJob.setValidationName(batchValidationRequest.getName());
                
                // Execute validation and collect data
                BatchSummaryReportGenerator.RequestExecutionData execData = 
                    executeValidationAndCollectData(individualJobId, jobRequest, batchValidationRequest);
                
                if (execData != null) {
                    batchExecutionData.put(batchValidationRequest.getName(), execData);
                    requestResults.add(new BatchExecutionResult.RequestResult(
                        batchValidationRequest.getName(),
                        individualJobId,
                        true,
                        null, null, null, 0, 0, 0
                    ));
                }
                
                successfulRequests++;
                log.info("Individual job {} completed successfully", individualJobId);
                
            } catch (Exception e) {
                failedRequests++;
                log.error("Individual job {} failed", individualJobId, e);
                
                requestResults.add(new BatchExecutionResult.RequestResult(
                    batchValidationRequest.getName(),
                    individualJobId,
                    false,
                    e.getMessage(), null, null, 0, 0, 0
                ));
                
                // Mark individual job as failed if it was created
                try {
                    jobService.failJob(individualJobId, e.getMessage());
                } catch (Exception ex) {
                    log.warn("Could not mark individual job as failed: {}", ex.getMessage());
                }
                
                // Continue with next request if continueOnError is true
                if (batchRequest.getSettings() != null && !batchRequest.getSettings().isContinueOnError()) {
                    log.warn("Stopping batch execution due to failure and continueOnError=false");
                    break;
                }
            } finally {
                completedRequests++;
            }
        }
        
        // Generate batch summary report
        try {
            if (!batchExecutionData.isEmpty()) {
                Path batchResultsDir = jobService.createJobResultsDirectory(batchJobId);
                File batchSummaryFile = batchResultsDir.resolve("batch-summary.xlsx").toFile();
                
                BatchExecutionResult batchResult = new BatchExecutionResult(
                    java.time.LocalDateTime.now().minusMinutes(1), // approximate start time
                    java.time.LocalDateTime.now(),
                    totalRequests,
                    successfulRequests,
                    failedRequests,
                    requestResults,
                    batchResultsDir.toString()
                );
                
                BatchSummaryReportGenerator summaryGenerator = new BatchSummaryReportGenerator();
                summaryGenerator.generateBatchSummaryReport(
                    batchResult, batchExecutionData, batchSummaryFile.getAbsolutePath()
                );
                
                log.info("Generated batch summary report: {}", batchSummaryFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to generate batch summary report", e);
            // Don't fail the entire batch if summary generation fails
        }
        
        // Complete batch job
        String message = String.format("Batch validation completed: %d successful, %d failed out of %d total",
                successfulRequests, failedRequests, totalRequests);
        
        jobService.completeJob(batchJobId, null, successfulRequests, failedRequests);
        jobService.updateProgress(batchJobId, JobProgress.builder()
                .currentStep(message)
                .percentage(100)
                .build());
        
        log.info("Batch job {} completed: {} successful, {} failed", 
                batchJobId, successfulRequests, failedRequests);
    }
    
    /**
     * Convert batch ValidationRequest to ValidationJobRequest
     */
    private ValidationJobRequest convertBatchRequestToJobRequest(
            com.nfv.validator.model.batch.ValidationRequest batchRequest,
            BatchValidationRequest batchValidationRequest) {
        ValidationJobRequest jobRequest = new ValidationJobRequest();
        jobRequest.setNamespaces(batchRequest.getNamespaces());
        jobRequest.setDescription(batchRequest.getName());
        jobRequest.setExportExcel(true);
        
        // Copy cluster info - CRITICAL for CNF checklist to use correct vimName
        if (batchRequest.getDefaultCluster() != null) {
            jobRequest.setCluster(batchRequest.getDefaultCluster());
            log.info("Set cluster to '{}' from defaultCluster", batchRequest.getDefaultCluster());
        }
        
        // Copy kinds filter
        if (batchRequest.getKinds() != null) {
            jobRequest.setKinds(batchRequest.getKinds());
        }
        
        // Merge ignore fields: start with global defaults, then add request-specific
        List<String> mergedIgnoreFields = new ArrayList<>();
        
        log.info("DEBUG convertBatchRequestToJobRequest: batchValidationRequest={}, settings={}", 
                 batchValidationRequest != null ? batchValidationRequest.getClass().getSimpleName() : "null",
                 batchValidationRequest != null && batchValidationRequest.getSettings() != null ? "present" : "null");
        
        // Add global ignore fields from settings
        if (batchValidationRequest.getSettings() != null && 
            batchValidationRequest.getSettings().getIgnoreFields() != null) {
            mergedIgnoreFields.addAll(batchValidationRequest.getSettings().getIgnoreFields());
            log.info("Added {} global ignore fields from batch settings", 
                     batchValidationRequest.getSettings().getIgnoreFields().size());
        } else {
            log.warn("No global ignore fields: settings={}, ignoreFields={}", 
                    batchValidationRequest != null && batchValidationRequest.getSettings() != null ? "present" : "null",
                    batchValidationRequest != null && batchValidationRequest.getSettings() != null && batchValidationRequest.getSettings().getIgnoreFields() != null ? "present" : "null");
        }
        
        // Add request-specific ignore fields (can override or add to global)
        if (batchRequest.getIgnoreFields() != null) {
            for (String field : batchRequest.getIgnoreFields()) {
                if (!mergedIgnoreFields.contains(field)) {
                    mergedIgnoreFields.add(field);
                }
            }
            log.info("Added {} request-specific ignore fields", batchRequest.getIgnoreFields().size());
        }
        
        if (!mergedIgnoreFields.isEmpty()) {
            jobRequest.setIgnoreFields(mergedIgnoreFields);
            log.info("Total ignore fields for this validation: {}", mergedIgnoreFields.size());
        }
        
        // Handle YAML content baseline (from frontend upload)
        if (batchRequest.getBaselineYamlContent() != null && !batchRequest.getBaselineYamlContent().trim().isEmpty()) {
            log.info("Using YAML content baseline ({} characters)", batchRequest.getBaselineYamlContent().length());
            jobRequest.setBaselineYamlContent(batchRequest.getBaselineYamlContent());
        }
        
        // Handle flattened baseline (direct model)
        if (batchRequest.getFlattenedBaseline() != null && !batchRequest.getFlattenedBaseline().isEmpty()) {
            log.info("Using flattened baseline with {} namespace models", batchRequest.getFlattenedBaseline().size());
            jobRequest.setFlattenedBaseline(batchRequest.getFlattenedBaseline());
        }
        
        return jobRequest;
    }
    
    /**
     * Execute the actual validation logic
     */
    private void executeValidation(String jobId, ValidationJobRequest request) throws Exception {
        log.info("Starting validation execution for job {}", jobId);
        
        // Mark job as processing
        jobService.startJob(jobId);
        
        // Load validation config - always use validation-config.yaml as default if not specified
        String configFile = request.getConfigFile();
        if (configFile == null || configFile.isEmpty()) {
            configFile = "validation-config.yaml"; // Ensure default config is always used
        }
        ValidationConfig validationConfig = configLoader.load(configFile);
        log.info("Loaded default validation config with {} ignore rules", 
                validationConfig.getIgnoreFields().size());
        
        // Merge with custom ignore fields from request if provided
        if (request.getIgnoreFields() != null && !request.getIgnoreFields().isEmpty()) {
            log.info("Request contains {} ignore fields: {}", 
                    request.getIgnoreFields().size(), request.getIgnoreFields());
            for (String customIgnoreField : request.getIgnoreFields()) {
                validationConfig.addIgnoreField(customIgnoreField);
            }
            log.info("Added {} custom ignore fields from request. Total ignore rules: {}", 
                    request.getIgnoreFields().size(), validationConfig.getIgnoreFields().size());
            log.info("All ignore patterns now active: {}", validationConfig.getIgnoreFields());
        } else {
            log.info("No custom ignore fields in request. Using default config with {} rules", 
                    validationConfig.getIgnoreFields().size());
        }
        
        // Update progress
        updateProgress(jobId, "Loading configuration", 5, 0, request.getNamespaces().size(), 0);
        
        // Load baseline if specified
        FlatNamespaceModel baselineModel = null;
        if (request.getFlattenedBaseline() != null && !request.getFlattenedBaseline().isEmpty()) {
            // Flattened baseline from CNF checklist conversion
            updateProgress(jobId, "Loading flattened baseline data", 10, 0, request.getNamespaces().size(), 0);
            
            // For CNF validation, we expect exactly one namespace model
            Map<String, FlatNamespaceModel> flatBaselines = request.getFlattenedBaseline();
            if (flatBaselines.size() == 1) {
                baselineModel = flatBaselines.values().iterator().next();
                log.info("Loaded flattened baseline with {} objects from namespace: {}", 
                        baselineModel.getObjects().size(), baselineModel.getName());
            } else {
                log.warn("Expected 1 flattened baseline model, but got {}. Using first one.", flatBaselines.size());
                if (!flatBaselines.isEmpty()) {
                    baselineModel = flatBaselines.values().iterator().next();
                }
            }
        } else if (request.getBaselineObjects() != null && !request.getBaselineObjects().isEmpty()) {
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
        } else if (request.getBaselineYamlContent() != null && !request.getBaselineYamlContent().trim().isEmpty()) {
            // Client-side baseline YAML content
            updateProgress(jobId, "Loading baseline YAML content", 10, 0, request.getNamespaces().size(), 0);
            
            try {
                // Write YAML content to temporary file
                java.io.File tempFile = java.io.File.createTempFile("baseline-", ".yaml");
                tempFile.deleteOnExit();
                java.nio.file.Files.write(tempFile.toPath(), request.getBaselineYamlContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
                log.info("Temp YAML file created: {}, size: {} bytes", tempFile.getAbsolutePath(), tempFile.length());
                
                YamlDataCollector yamlCollector = new YamlDataCollector();
                baselineModel = yamlCollector.collectFromYaml(tempFile.getAbsolutePath(), "baseline");
                
                log.info("Loaded {} objects from baseline YAML content, model name: {}", 
                         baselineModel.getObjects().size(), baselineModel.getName());
                log.info("First few objects: {}", 
                         baselineModel.getObjects().keySet().stream().limit(5).collect(java.util.stream.Collectors.toList()));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to process baseline YAML content", e);
            }
        }
        
        // Parse namespace targets
        List<NamespaceTarget> targets = new ArrayList<>();
        // For CNF checklist or batch with defaultCluster, use cluster from request (which contains vimName)
        // Otherwise fallback to "current"
        String defaultCluster = request.getCluster() != null ? request.getCluster() : "current";
        
        log.info("Using defaultCluster='{}' for namespace collection", defaultCluster);
        
        for (String nsArg : request.getNamespaces()) {
            targets.add(parseNamespaceArg(nsArg, defaultCluster));
        }
        
        // Collect data from all namespaces
        updateProgress(jobId, "Collecting namespace data", 20, 0, targets.size(), 0);
        
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        
        if (baselineModel != null) {
            namespaceModels.add(baselineModel);
            log.info("Added baseline to namespaceModels. Total models: {}, baseline has {} objects", 
                     namespaceModels.size(), baselineModel.getObjects().size());
        }
        
        // Determine if we need semantic V2 collection (for identity matching)
        boolean useSemanticV2 = false;
        if (request.getCnfChecklistRequest() instanceof CNFChecklistRequest) {
            CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
            String matchingStrategy = cnfRequest.getMatchingStrategy();
            useSemanticV2 = "identity".equalsIgnoreCase(matchingStrategy);
            log.info("CNF Checklist detected with matchingStrategy: {}, useSemanticV2: {}", 
                     matchingStrategy, useSemanticV2);
        }
        
        int objectsCollected = 0;
        for (int i = 0; i < targets.size(); i++) {
            NamespaceTarget target = targets.get(i);
            
            updateProgress(jobId, "Collecting: " + target.namespaceName, 
                    20 + (40 * i / targets.size()), i, targets.size(), objectsCollected);
            
            KubernetesClient client = clusterManager.getClient(target.clusterName);
            
            FlatNamespaceModel model;
            if (useSemanticV2) {
                // Use semantic V2 collection for identity matching
                log.info("[V2] Using K8sDataCollectorV2 for {}/{}", target.clusterName, target.namespaceName);
                K8sDataCollectorV2 collectorV2 = new K8sDataCollectorV2(client);
                
                // V2 currently doesn't support kind filtering, collect all
                SemanticNamespaceModel semanticModel = collectorV2.collectNamespace(
                        target.namespaceName, target.clusterName);
                
                log.info("[V2] Collected semantic model with {} objects", semanticModel.getObjects().size());
                
                // Convert semantic to flat for comparison
                SemanticToFlatAdapter adapter = new SemanticToFlatAdapter();
                model = adapter.toFlatModel(semanticModel);
                
                log.info("[V2] Converted to flat model with {} objects, {} total keys", 
                         model.getObjects().size(), 
                         model.getObjects().values().stream()
                             .mapToInt(obj -> obj.getSpec().size() + obj.getMetadata().size())
                             .sum());
                
                // Debug: log sample keys and values
                if (!model.getObjects().isEmpty()) {
                    FlatObjectModel firstObj = model.getObjects().values().iterator().next();
                    List<String> sampleKeys = firstObj.getSpec().keySet().stream().limit(5)
                        .collect(java.util.stream.Collectors.toList());
                    log.info("[V2] Sample keys from {}: {}", firstObj.getName(), sampleKeys);
                    // Log first value to check if data exists
                    if (!sampleKeys.isEmpty()) {
                        String firstKey = sampleKeys.get(0);
                        String firstValue = firstObj.getSpec().get(firstKey);
                        log.info("[V2] Sample value for '{}': '{}'", firstKey, firstValue);
                    }
                }
            } else {
                // Use V1 flat collection
                K8sDataCollector collector = new K8sDataCollector(client);
                
                if (request.getKinds() != null && !request.getKinds().isEmpty()) {
                    model = collector.collectNamespaceByKinds(
                            target.namespaceName, target.clusterName, request.getKinds());
                } else {
                    model = collector.collectNamespace(target.namespaceName, target.clusterName);
                }
            }
            
            namespaceModels.add(model);
            objectsCollected += model.getObjects().size();
            
            log.info("Collected {} objects from {}/{}", 
                    model.getObjects().size(), target.clusterName, target.namespaceName);
        }
        
        // Filter ignored fields from all collected models BEFORE comparison
        // This improves performance and prevents ignored fields from appearing in results
        updateProgress(jobId, "Filtering ignored fields", 55, targets.size(), targets.size(), objectsCollected);
        
        if (baselineModel != null) {
            validationConfig.filterIgnoredFields(baselineModel);
        }
        
        for (FlatNamespaceModel nsModel : namespaceModels) {
            // Skip baseline if already filtered
            if (nsModel != baselineModel) {
                validationConfig.filterIgnoredFields(nsModel);
            }
        }
        
        log.info("Filtered ignored fields from {} namespace models using {} ignore rules", 
                namespaceModels.size(), validationConfig.getIgnoreFields().size());
        
        // Perform comparisons
        updateProgress(jobId, "Comparing namespaces", 60, targets.size(), targets.size(), objectsCollected);
        
        Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();
        int totalDifferences = 0;
        
        if (baselineModel != null) {
            // Check if this is from flattened baseline (CNF mode)
            boolean isFlattenedBaseline = request.getFlattenedBaseline() != null && !request.getFlattenedBaseline().isEmpty();
            
            if (isFlattenedBaseline) {
                // CNF mode - use filtered comparison
                // Compare baseline with actual targets (skip baseline in namespaceModels[0])
                log.info("CNF mode: comparing baseline '{}' against {} target namespaces", 
                         baselineModel.getName(), namespaceModels.size() - 1);
                
                Map<String, FlatNamespaceModel> flatBaselines = request.getFlattenedBaseline();
                
                for (int i = 1; i < namespaceModels.size(); i++) {
                    FlatNamespaceModel ns = namespaceModels.get(i);
                    log.info("Comparing flattened baseline vs {} ({} objects)", 
                             ns.getName(), ns.getObjects().size());
                    
                    String comparisonKey = buildComparisonKey(baselineModel, ns);
                    NamespaceComparison comparison = NamespaceComparator.compareWithFlattenedBaseline(
                            flatBaselines, ns.getObjects(), ns.getName(), validationConfig);
                    
                    comparisons.put(comparisonKey, comparison);
                    totalDifferences += comparison.getSummary().getDifferencesCount();
                    
                    log.info("CNF comparison key: {}, differences: {}", comparisonKey, comparison.getSummary().getDifferencesCount());
                }
            } else {
                // Normal baseline mode - compare all fields
                log.info("Baseline comparison mode: comparing {} namespaces against baseline '{}'", 
                         namespaceModels.size() - 1, baselineModel.getName());
                log.info("ValidationConfig has {} ignore rules: {}", 
                         validationConfig.getIgnoreFields().size(), 
                         validationConfig.getIgnoreFields());
                log.info("UseSemanticComparison = {}", FeatureFlags.getInstance().isUseSemanticComparison());
                log.info("ValidationConfig has {} ignore rules", validationConfig.getIgnoreFields().size());
                log.info("UseSemanticComparison = {}", FeatureFlags.getInstance().isUseSemanticComparison());
                
                for (int i = 1; i < namespaceModels.size(); i++) {
                    FlatNamespaceModel ns = namespaceModels.get(i);
                    
                    log.info("Comparing baseline ({} objects) vs {} ({} objects)", 
                             baselineModel.getObjects().size(), ns.getName(), ns.getObjects().size());
                    
                    String comparisonKey = buildComparisonKey(baselineModel, ns);
                    NamespaceComparison comparison;
                    if (FeatureFlags.getInstance().isUseSemanticComparison()) {
                        log.info("[V2] Using semantic comparison for {}", comparisonKey);
                        comparison = ValidationServiceV2.compareFlat(
                                baselineModel, ns,
                                baselineModel.getClusterName() + "/" + baselineModel.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    } else {
                        comparison = NamespaceComparator.compareNamespace(
                                baselineModel.getObjects(), ns.getObjects(),
                                baselineModel.getClusterName() + "/" + baselineModel.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    }
                    
                    comparisons.put(comparisonKey, comparison);
                    totalDifferences += comparison.getSummary().getDifferencesCount();
                    
                    log.info("Comparison key: {}, differences: {}", comparisonKey, comparison.getSummary().getDifferencesCount());
                }
            }
        } else if (namespaceModels.size() >= 2) {
            // No explicit baseline - use first namespace as baseline
            // Compare all other namespaces against the first one
            FlatNamespaceModel firstNamespace = namespaceModels.get(0);
            
            for (int i = 1; i < namespaceModels.size(); i++) {
                FlatNamespaceModel ns = namespaceModels.get(i);
                
                String comparisonKey = buildComparisonKey(firstNamespace, ns);
                NamespaceComparison comparison;
                if (FeatureFlags.getInstance().isUseSemanticComparison()) {
                    log.info("[V2] Using semantic comparison for {}", comparisonKey);
                    comparison = ValidationServiceV2.compareFlat(
                            firstNamespace, ns,
                            firstNamespace.getClusterName() + "/" + firstNamespace.getName(),
                            ns.getClusterName() + "/" + ns.getName(),
                            validationConfig);
                } else {
                    comparison = NamespaceComparator.compareNamespace(
                            firstNamespace.getObjects(), ns.getObjects(),
                            firstNamespace.getClusterName() + "/" + firstNamespace.getName(),
                            ns.getClusterName() + "/" + ns.getName(),
                            validationConfig);
                }
                
                comparisons.put(comparisonKey, comparison);
                totalDifferences += comparison.getSummary().getDifferencesCount();
            }
        } else {
            // Only one namespace - no comparisons possible
            log.warn("Only one namespace provided, no comparisons will be performed");
        }
        
        // Create results directory
        Path resultsDir = jobService.createJobResultsDirectory(jobId);
        
        // Export to JSON
        updateProgress(jobId, "Generating JSON results", 80, targets.size(), targets.size(), objectsCollected);
        
        File jsonFile = resultsDir.resolve("validation-results.json").toFile();
        jsonExporter.exportToJson(jobId, comparisons, request.getDescription(), jsonFile);
        
        log.info("Exported JSON results to {}", jsonFile.getAbsolutePath());
        
        // Export CNF-specific JSON if this is a CNF checklist request
        if (request.getCnfChecklistRequest() != null) {
            File cnfJsonFile = resultsDir.resolve("cnf-results.json").toFile();
            exportCnfJson(jobId, request, comparisons, cnfJsonFile, targets.size());
            log.info("Exported CNF JSON results (with flexible matching): {}", cnfJsonFile.getAbsolutePath());
        }
        
        // Export to Excel if requested
        if (request.getExportExcel() == null || request.getExportExcel()) {
            updateProgress(jobId, "Generating Excel report", 90, targets.size(), targets.size(), objectsCollected);
            
            // Check if this is a CNF checklist request
            if (request.getCnfChecklistRequest() != null && request.getCnfChecklistRequest() instanceof CNFChecklistRequest) {
                // Generate CNF-specific Excel report
                CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
                List<CnfComparison> cnfComparisons = cnfChecklistService.convertToCnfComparison(cnfRequest, comparisons);
                
                File cnfExcelFile = resultsDir.resolve("cnf-checklist-validation.xlsx").toFile();
                CNFChecklistExcelGenerator cnfExcelGenerator = new CNFChecklistExcelGenerator();
                cnfExcelGenerator.generateReport(cnfRequest, cnfComparisons, cnfExcelFile.getAbsolutePath());
                
                log.info("Exported CNF Checklist Excel report to {}", cnfExcelFile.getAbsolutePath());
            } else {
                // Generate standard Excel report
                File excelFile = resultsDir.resolve("validation-report.xlsx").toFile();
                ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
                excelGenerator.generateReport(namespaceModels, comparisons, excelFile.getAbsolutePath(), validationConfig);
                
                log.info("Exported Excel report to {}", excelFile.getAbsolutePath());
            }
        }
        
        // Mark job as completed
        jobService.completeJob(jobId, resultsDir.toString(), objectsCollected, totalDifferences);
        
        log.info("Validation job {} completed successfully", jobId);
    }
    
    /**
     * Execute validation and collect data for batch summary
     */
    private BatchSummaryReportGenerator.RequestExecutionData executeValidationAndCollectData(
            String jobId, ValidationJobRequest request, com.nfv.validator.model.batch.ValidationRequest batchRequest) throws Exception {
        
        log.info("Starting validation execution for job {} with data collection", jobId);
        
        // Execute normal validation
        executeValidation(jobId, request);
        
        // Reload comparison data for batch summary
        // We need to parse namespaceModels and comparisons from the execution
        try {
            // Load validation config - always use validation-config.yaml as default if not specified
            String configFile = request.getConfigFile();
            if (configFile == null || configFile.isEmpty()) {
                configFile = "validation-config.yaml";
            }
            ValidationConfig validationConfig = configLoader.load(configFile);
            
            // Collect namespace models (same logic as executeValidation)
            List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
            
            // Load baseline if specified
            FlatNamespaceModel baselineModel = null;
            if (request.getBaselineObjects() != null && !request.getBaselineObjects().isEmpty()) {
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
            } else if (request.getBaselineYamlContent() != null && !request.getBaselineYamlContent().trim().isEmpty()) {
                java.io.File tempFile = java.io.File.createTempFile("baseline-", ".yaml");
                tempFile.deleteOnExit();
                java.nio.file.Files.write(tempFile.toPath(), request.getBaselineYamlContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
                YamlDataCollector yamlCollector = new YamlDataCollector();
                baselineModel = yamlCollector.collectFromYaml(tempFile.getAbsolutePath(), "baseline");
            }
            
            if (baselineModel != null) {
                namespaceModels.add(baselineModel);
            }
            
            // Parse namespace targets
            List<NamespaceTarget> targets = new ArrayList<>();
            String defaultCluster = request.getCluster() != null ? request.getCluster() : "current";
            
            for (String nsArg : request.getNamespaces()) {
                targets.add(parseNamespaceArg(nsArg, defaultCluster));
            }
            
            // Collect from Kubernetes
            for (NamespaceTarget target : targets) {
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
            }
            
            // Perform comparisons
            Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();
            
            if (baselineModel != null) {
                // Baseline mode
                for (int i = 1; i < namespaceModels.size(); i++) {
                    FlatNamespaceModel ns = namespaceModels.get(i);
                    String comparisonKey = buildComparisonKey(baselineModel, ns);
                    NamespaceComparison comparison;
                    if (FeatureFlags.getInstance().isUseSemanticComparison()) {
                        log.info("[V2] Using semantic comparison for {}", comparisonKey);
                        comparison = ValidationServiceV2.compareFlat(
                                baselineModel, ns,
                                baselineModel.getClusterName() + "/" + baselineModel.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    } else {
                        comparison = NamespaceComparator.compareNamespace(
                                baselineModel.getObjects(), ns.getObjects(),
                                baselineModel.getClusterName() + "/" + baselineModel.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    }
                    comparisons.put(comparisonKey, comparison);
                }
            } else if (namespaceModels.size() >= 2) {
                // No baseline - use first as baseline
                FlatNamespaceModel firstNamespace = namespaceModels.get(0);
                for (int i = 1; i < namespaceModels.size(); i++) {
                    FlatNamespaceModel ns = namespaceModels.get(i);
                    String comparisonKey = buildComparisonKey(firstNamespace, ns);
                    NamespaceComparison comparison;
                    if (FeatureFlags.getInstance().isUseSemanticComparison()) {
                        log.info("[V2] Using semantic comparison for {}", comparisonKey);
                        comparison = ValidationServiceV2.compareFlat(
                                firstNamespace, ns,
                                firstNamespace.getClusterName() + "/" + firstNamespace.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    } else {
                        comparison = NamespaceComparator.compareNamespace(
                                firstNamespace.getObjects(), ns.getObjects(),
                                firstNamespace.getClusterName() + "/" + firstNamespace.getName(),
                                ns.getClusterName() + "/" + ns.getName(),
                                validationConfig);
                    }
                    comparisons.put(comparisonKey, comparison);
                }
            }
            
            return new BatchSummaryReportGenerator.RequestExecutionData(
                    namespaceModels, comparisons, batchRequest
            );
            
        } catch (Exception e) {
            log.error("Failed to collect batch summary data for job {}", jobId, e);
            return null;
        }
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
     * Execute CNF checklist validation
     */
    private void executeCNFChecklistValidation(String jobId, ValidationJobRequest request,
                                                Map<String, FlatNamespaceModel> baselineMap) throws Exception {
        log.info("Starting CNF checklist validation for job {}", jobId);
        
        // Mark job as processing
        jobService.startJob(jobId);
        
        // Determine if semantic V2 should be used based on matching strategy from original CNF request
        boolean useSemanticV2 = false;
        if (request.getCnfChecklistRequest() instanceof CNFChecklistRequest) {
            CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
            useSemanticV2 = cnfRequest.shouldUseSemanticV2();
            log.info("CNF Checklist async: matchingStrategy='{}', useSemanticV2={}", 
                    cnfRequest.getMatchingStrategy(), useSemanticV2);
        } else {
            // Fallback to feature flag
            useSemanticV2 = FeatureFlags.getInstance().isUseSemanticComparison();
        }
        
        // Load validation config - always use validation-config.yaml as default if not specified
        String configFile = request.getConfigFile();
        if (configFile == null || configFile.isEmpty()) {
            configFile = "validation-config.yaml";
        }
        ValidationConfig validationConfig = configLoader.load(configFile);
        log.info("Loaded default validation config with {} ignore rules for CNF validation", 
                validationConfig.getIgnoreFields().size());
        
        // Merge with custom ignore fields from CNF request if provided
        if (request.getCnfChecklistRequest() instanceof CNFChecklistRequest) {
            CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
            if (cnfRequest.getIgnoreFields() != null && !cnfRequest.getIgnoreFields().isEmpty()) {
                for (String customIgnoreField : cnfRequest.getIgnoreFields()) {
                    validationConfig.addIgnoreField(customIgnoreField);
                }
                log.info("Added {} custom ignore fields from CNF request. Total ignore rules: {}", 
                        cnfRequest.getIgnoreFields().size(), validationConfig.getIgnoreFields().size());
            }
        }
        
        // Also merge with custom ignore fields from job request if provided
        if (request.getIgnoreFields() != null && !request.getIgnoreFields().isEmpty()) {
            for (String customIgnoreField : request.getIgnoreFields()) {
                validationConfig.addIgnoreField(customIgnoreField);
            }
            log.info("Added {} custom ignore fields from job request. Total ignore rules: {}", 
                    request.getIgnoreFields().size(), validationConfig.getIgnoreFields().size());
        }
        
        // Update progress
        updateProgress(jobId, "Preparing CNF checklist validation", 5, 0, baselineMap.size(), 0);
        
        // Collect data from all namespaces in checklist
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();
        int totalDifferences = 0;
        int objectsCollected = 0;
        
        int namespaceIndex = 0;
        for (Map.Entry<String, FlatNamespaceModel> entry : baselineMap.entrySet()) {
            String namespaceKey = entry.getKey(); // vimName/namespace
            FlatNamespaceModel baselineModel = entry.getValue();
            
            String vimName = baselineModel.getClusterName();
            String namespace = baselineModel.getName();
            
            updateProgress(jobId, "Validating " + namespaceKey, 
                    20 + (60 * namespaceIndex / baselineMap.size()), 
                    namespaceIndex, baselineMap.size(), objectsCollected);
            
            log.info("Processing CNF checklist for {}: {} baseline objects", 
                    namespaceKey, baselineModel.getObjects().size());
            
            // Collect actual data from K8s cluster using vimName as cluster
            KubernetesClient client = clusterManager.getClient(vimName);
            FlatNamespaceModel actualModel;
            
            if (useSemanticV2) {
                // Use V2 collector for semantic comparison
                log.info("[V2] Using semantic collector for runtime data (async): {}/{}", vimName, namespace);
                K8sDataCollectorV2 collectorV2 = new K8sDataCollectorV2(client);
                SemanticNamespaceModel semanticRuntime = collectorV2.collectNamespace(namespace, vimName);
                
                // Convert semantic back to flat for comparison
                actualModel = SemanticToFlatAdapter.toFlatModel(semanticRuntime);
            } else {
                // Use V1 flat collector
                K8sDataCollector collector = new K8sDataCollector(client);
                if (request.getKinds() != null && !request.getKinds().isEmpty()) {
                    actualModel = collector.collectNamespaceByKinds(
                            namespace, vimName, request.getKinds());
                } else {
                    actualModel = collector.collectNamespace(namespace, vimName);
                }
            }
            
            objectsCollected += actualModel.getObjects().size();
            log.info("Collected {} actual objects from {}/{}", 
                    actualModel.getObjects().size(), vimName, namespace);
            
            // Filter ignored fields from BOTH baseline and actual models BEFORE comparison
            // When user adds ignore rules, they want to ignore those fields completely
            // (not validate them and not show them in results)
            validationConfig.filterIgnoredFields(baselineModel);
            validationConfig.filterIgnoredFields(actualModel);
            log.debug("Filtered ignored fields from baseline and actual for CNF comparison: {}", namespaceKey);
            
            // Debug logging for CNF checklist
            log.info("DEBUG CNF: vimName='{}', baselineModel.getClusterName()='{}', actualModel.getClusterName()='{}'", 
                    vimName, baselineModel.getClusterName(), actualModel.getClusterName());
            
            // Add models for comparison
            namespaceModels.add(baselineModel);
            namespaceModels.add(actualModel);
            
            // Compare baseline vs actual for this namespace
            String comparisonKey = buildComparisonKey(baselineModel, actualModel);
            log.info("DEBUG CNF: comparisonKey='{}'", comparisonKey);
            
            // Use descriptive names for comparison labels
            String baselineLabel = vimName + "/" + namespace + " (Baseline)";
            String actualLabel = vimName + "/" + namespace + " (Actual)";
            
            NamespaceComparison comparison;
            if (useSemanticV2) {
                log.info("[V2] Using semantic comparison for CNF checklist async: {}", comparisonKey);
                comparison = ValidationServiceV2.compareFlat(
                        baselineModel, actualModel,
                        baselineLabel, actualLabel,
                        validationConfig);
            } else {
                comparison = NamespaceComparator.compareNamespace(
                        baselineModel.getObjects(), actualModel.getObjects(),
                        baselineLabel, actualLabel,
                        validationConfig);
            }
            
            comparisons.put(comparisonKey, comparison);
            totalDifferences += comparison.getSummary().getDifferencesCount();
            
            log.info("Comparison for {}: {} differences found", 
                    namespaceKey, comparison.getSummary().getDifferencesCount());
            
            namespaceIndex++;
        }
        
        // Create results directory
        Path resultsDir = jobService.createJobResultsDirectory(jobId);
        log.info("Created results directory: {}", resultsDir);
        
        // Convert comparisons to CNF format
        updateProgress(jobId, "Converting to CNF format", 80, baselineMap.size(), baselineMap.size(), objectsCollected);
        
        CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
        List<CnfComparison> cnfComparisons = cnfChecklistService.convertToCnfComparison(cnfRequest, comparisons);
        
        log.info("Converted {} namespace comparisons to CNF format", cnfComparisons.size());
        
        // Generate CNF Checklist Excel report
        updateProgress(jobId, "Generating CNF Excel report", 85, baselineMap.size(), baselineMap.size(), objectsCollected);
        
        CNFChecklistExcelGenerator cnfExcelGenerator = new CNFChecklistExcelGenerator();
        String cnfExcelPath = resultsDir.resolve("cnf-checklist-validation.xlsx").toString();
        cnfExcelGenerator.generateReport(cnfRequest, cnfComparisons, cnfExcelPath);
        
        log.info("Generated CNF Checklist Excel report: {}", cnfExcelPath);
        
        // Export JSON results
        updateProgress(jobId, "Exporting JSON results", 95, baselineMap.size(), baselineMap.size(), objectsCollected);
        
        // Export standard JSON for Excel compatibility
        File jsonFile = resultsDir.resolve("validation-results.json").toFile();
        jsonExporter.exportToJson(jobId, comparisons, request.getDescription(), jsonFile);
        
        log.info("Exported standard JSON results: {}", jsonFile.getAbsolutePath());
        
        // Export CNF-specific JSON for web UI
        File cnfJsonFile = resultsDir.resolve("cnf-results.json").toFile();
        exportCnfJson(jobId, request, comparisons, cnfJsonFile, baselineMap.size());
        
        log.info("Exported CNF JSON results: {}", cnfJsonFile.getAbsolutePath());
        
        // Complete the job
        jobService.completeJob(jobId, resultsDir.toString(), objectsCollected, totalDifferences);
        
        log.info("CNF checklist validation job {} completed: {} namespaces validated, {} differences found",
                jobId, baselineMap.size(), totalDifferences);
    }
    
    /**
     * Execute CNF checklist validation synchronously and return results
     */
    public ValidationResultJson executeCNFChecklistSync(CNFChecklistRequest request, 
                                                        Map<String, FlatNamespaceModel> baselineMap) throws Exception {
        log.info("Starting SYNC CNF checklist validation with {} baseline namespaces", baselineMap.size());
        
        // Load validation config - always use validation-config.yaml as default
        ValidationConfig validationConfig = configLoader.load("validation-config.yaml");
        
        // Collect data from all namespaces in checklist
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();
        int totalDifferences = 0;
        int objectsCollected = 0;
        
        long startTime = System.currentTimeMillis();
        
        for (Map.Entry<String, FlatNamespaceModel> entry : baselineMap.entrySet()) {
            String namespaceKey = entry.getKey(); // vimName/namespace
            FlatNamespaceModel baselineModel = entry.getValue();
            
            String vimName = baselineModel.getClusterName();
            String namespace = baselineModel.getName();
            
            log.info("Processing CNF checklist for {}: {} baseline objects", 
                    namespaceKey, baselineModel.getObjects().size());
            
            // Use V2 flag from request (determined by matchingStrategy), fallback to feature flag
            boolean useSemanticV2 = request.shouldUseSemanticV2() || FeatureFlags.getInstance().isUseSemanticComparison();
            
            // Collect actual data from K8s cluster using vimName as cluster
            KubernetesClient client = clusterManager.getClient(vimName);
            FlatNamespaceModel actualModel;
            
            if (useSemanticV2) {
                // Use V2 collector for semantic comparison
                log.info("[V2] Using semantic collector for runtime data: {}/{}", vimName, namespace);
                K8sDataCollectorV2 collectorV2 = new K8sDataCollectorV2(client);
                SemanticNamespaceModel semanticRuntime = collectorV2.collectNamespace(namespace, vimName);
                // Convert semantic back to flat for comparison
                actualModel = SemanticToFlatAdapter.toFlatModel(semanticRuntime);
            } else {
                // Use V1 flat collector
                K8sDataCollector collector = new K8sDataCollector(client);
                actualModel = collector.collectNamespace(namespace, vimName);
            }
            
            objectsCollected += actualModel.getObjects().size();
            log.info("Collected {} actual objects from {}/{}", 
                    actualModel.getObjects().size(), vimName, namespace);
            
            // Filter ignored fields from BOTH baseline and actual models BEFORE comparison
            // When user adds ignore rules, they want to ignore those fields completely
            // (not validate them and not show them in results)
            validationConfig.filterIgnoredFields(baselineModel);
            validationConfig.filterIgnoredFields(actualModel);
            log.debug("Filtered ignored fields from baseline and actual for CNF sync comparison: {}", namespaceKey);
            
            // Add models for comparison
            namespaceModels.add(baselineModel);
            namespaceModels.add(actualModel);
            
            // Compare baseline vs actual for this namespace
            String comparisonKey = buildComparisonKey(baselineModel, actualModel);
            
            // Use descriptive names for comparison labels (matching async version)
            String baselineLabel = vimName + "/" + namespace + " (Baseline)";
            String actualLabel = vimName + "/" + namespace + " (Actual)";
            
            NamespaceComparison comparison;
            
            if (useSemanticV2) {
                log.info("[V2] Using semantic comparison for CNF checklist sync: {} (strategy: {})", 
                        comparisonKey, request.getMatchingStrategy());
                comparison = ValidationServiceV2.compareFlat(
                        baselineModel, actualModel,
                        baselineLabel, actualLabel,
                        validationConfig);
            } else {
                comparison = NamespaceComparator.compareNamespace(
                        baselineModel.getObjects(), actualModel.getObjects(),
                        baselineLabel, actualLabel,
                        validationConfig);
            }
            
            comparisons.put(comparisonKey, comparison);
            totalDifferences += comparison.getSummary().getDifferencesCount();
            
            log.info("Comparison for {}: {} differences found", 
                    namespaceKey, comparison.getSummary().getDifferencesCount());
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Convert comparisons to CNF format
        List<CnfComparison> cnfComparisons = cnfChecklistService.convertToCnfComparison(request, comparisons);
        log.info("Converted {} namespace comparisons to CNF format (sync)", cnfComparisons.size());
        
        // Build and return result JSON
        ValidationResultJson result = new ValidationResultJson();
        result.setJobId("cnf-sync-" + System.currentTimeMillis());
        result.setSubmittedAt(java.time.Instant.now());
        result.setCompletedAt(java.time.Instant.now());
        result.setDescription(request.getDescription() != null ? 
            request.getDescription() : "CNF Checklist Validation (Sync)");
        
        // Create summary
        ValidationResultJson.SummaryStats summary = new ValidationResultJson.SummaryStats();
        summary.setTotalObjects(objectsCollected);
        summary.setTotalDifferences(totalDifferences);
        summary.setNamespacePairs(baselineMap.size());
        summary.setExecutionTimeMs(executionTime);
        result.setSummary(summary);
        
        result.setComparisons(comparisons);
        
        log.info("SYNC CNF checklist validation completed: {} namespaces, {} differences, {}ms",
                baselineMap.size(), totalDifferences, executionTime);
        
        return result;
    }
    
    /**
     * Export CNF-specific JSON format for web UI
     */
    private void exportCnfJson(String jobId, ValidationJobRequest request,
                               Map<String, NamespaceComparison> comparisons,
                               File outputFile, int namespaceCount) throws Exception {
        
        // Get original CNF request from job context
        CNFChecklistRequest cnfRequest = (CNFChecklistRequest) request.getCnfChecklistRequest();
        
        if (cnfRequest == null) {
            log.warn("No CNF checklist request found in job context");
            return;
        }
        
        // Convert to CNF-specific format
        List<com.nfv.validator.model.comparison.CnfComparison> cnfResults = 
                cnfChecklistService.convertToCnfComparison(cnfRequest, comparisons);
        
        // Calculate overall summary
        int totalFields = 0;
        int totalMatches = 0;
        int totalDifferences = 0;
        int totalMissing = 0;
        int totalErrors = 0;
        
        for (com.nfv.validator.model.comparison.CnfComparison cnfComp : cnfResults) {
            com.nfv.validator.model.comparison.CnfComparison.CnfSummary summary = cnfComp.getSummary();
            totalFields += summary.getTotalFields();
            totalMatches += summary.getMatchCount();
            totalDifferences += summary.getDifferenceCount();
            totalMissing += summary.getMissingCount();
            totalErrors += summary.getErrorCount();
        }
        
        // Build CNF validation result JSON
        com.nfv.validator.model.api.CnfValidationResultJson result = 
                com.nfv.validator.model.api.CnfValidationResultJson.builder()
                .jobId(jobId)
                .submittedAt(java.time.Instant.now())
                .completedAt(java.time.Instant.now())
                .description(request.getDescription())
                .results(cnfResults)
                .summary(com.nfv.validator.model.api.CnfValidationResultJson.CnfOverallSummary.builder()
                        .totalVimNamespaces(namespaceCount)
                        .totalFields(totalFields)
                        .totalMatches(totalMatches)
                        .totalDifferences(totalDifferences)
                        .totalMissing(totalMissing)
                        .totalErrors(totalErrors)
                        .executionTimeMs(0) // Will be updated
                        .build())
                .build();
        
        // Write to file
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result);
        
        log.info("Exported CNF JSON: {} VIM/namespaces, {} fields, {} matches, {} differences",
                namespaceCount, totalFields, totalMatches, totalDifferences);
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
