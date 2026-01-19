package com.nfv.validator.batch;

import com.nfv.validator.comparison.NamespaceComparator;
import com.nfv.validator.service.ValidationServiceV2;
import com.nfv.validator.config.ConfigLoader;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollector;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.batch.BatchExecutionResult;
import com.nfv.validator.model.batch.BatchValidationRequest;
import com.nfv.validator.model.batch.ValidationRequest;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.report.ExcelReportGenerator;
import com.nfv.validator.yaml.YamlDataCollector;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes batch validation requests
 * Supports both sequential and parallel execution
 */
@Slf4j
public class BatchExecutor {
    
    private final KubernetesClusterManager clusterManager;
    private final ConfigLoader configLoader;
    private final ExecutorService executorService;
    private final Map<String, BatchSummaryReportGenerator.RequestExecutionData> requestExecutionData;
    
    public BatchExecutor() {
        this.clusterManager = new KubernetesClusterManager();
        this.configLoader = new ConfigLoader();
        this.executorService = null; // Will be created based on maxParallelRequests
        this.requestExecutionData = new LinkedHashMap<>();
    }
    
    /**
     * Execute a batch validation request
     * 
     * @param batchRequest The batch request to execute
     * @return BatchExecutionResult containing results of all requests
     */
    public BatchExecutionResult execute(BatchValidationRequest batchRequest) {
        log.info("Starting batch execution with {} requests", batchRequest.getRequests().size());
        
        // Use timestamped output directory
        String actualOutputDir = batchRequest.getSettings() != null ? 
                batchRequest.getSettings().getOutputDirectoryWithTimestamp() : 
                "results/batch-results-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
        
        // Create output directory
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(actualOutputDir));
        } catch (Exception e) {
            log.error("Failed to create output directory: {}", actualOutputDir, e);
        }
        
        BatchExecutionResult result = new BatchExecutionResult();
        result.setStartTime(LocalDateTime.now());
        result.setTotalRequests(batchRequest.getRequests().size());
        result.setActualOutputDirectory(actualOutputDir);
        
        // Determine execution mode
        boolean continueOnError = true;
        int maxParallel = 1; // Default sequential
        
        if (batchRequest.getSettings() != null) {
            continueOnError = batchRequest.getSettings().isContinueOnError();
            maxParallel = Math.max(1, batchRequest.getSettings().getMaxParallelRequests());
        }
        
        try {
            if (maxParallel <= 1) {
                // Sequential execution
                executeSequential(batchRequest, result, continueOnError, actualOutputDir);
            } else {
                // Parallel execution
                executeParallel(batchRequest, result, continueOnError, maxParallel, actualOutputDir);
            }
        } catch (Exception e) {
            log.error("Batch execution failed with unexpected error", e);
        } finally {
            result.setEndTime(LocalDateTime.now());
        }
        
        log.info("Batch execution completed: {} successful, {} failed out of {} total",
                result.getSuccessfulRequests(), result.getFailedRequests(), result.getTotalRequests());
        
        // Generate batch summary report if configured
        if (batchRequest.getSettings() != null && 
            batchRequest.getSettings().isGenerateSummaryReport()) {
            
            try {
                System.out.println();
                System.out.println("📊 Generating batch summary report...");
                
                // Use configured path or default to "batch-summary.xlsx" in timestamped output directory
                String summaryPath = batchRequest.getSettings().getSummaryReportPath();
                if (summaryPath == null || summaryPath.trim().isEmpty()) {
                    summaryPath = actualOutputDir + "/batch-summary.xlsx";
                }
                
                BatchSummaryReportGenerator summaryGenerator = new BatchSummaryReportGenerator();
                summaryGenerator.generateBatchSummaryReport(
                        result, 
                        requestExecutionData, 
                        summaryPath
                );
                
                System.out.println("   ✓ Batch summary report saved to: " + summaryPath);
            } catch (Exception e) {
                log.error("Failed to generate batch summary report", e);
                System.err.println("❌ Failed to generate batch summary report: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Execute requests sequentially (one after another)
     */
    private void executeSequential(BatchValidationRequest batchRequest, 
                                   BatchExecutionResult result,
                                   boolean continueOnError,
                                   String actualOutputDir) {
        log.info("Executing requests sequentially");
        
        for (int i = 0; i < batchRequest.getRequests().size(); i++) {
            ValidationRequest request = batchRequest.getRequests().get(i);
            
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.printf("  Executing request %d/%d: %s%n", 
                    i + 1, batchRequest.getRequests().size(), request.getName());
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println();
            
            BatchExecutionResult.RequestResult requestResult = executeRequest(request, batchRequest, actualOutputDir);
            result.addRequestResult(requestResult);
            
            if (!requestResult.isSuccess() && !continueOnError) {
                log.warn("Request failed and continueOnError=false, stopping batch execution");
                break;
            }
        }
    }
    
    /**
     * Execute requests in parallel
     */
    private void executeParallel(BatchValidationRequest batchRequest,
                                 BatchExecutionResult result,
                                 boolean continueOnError,
                                 int maxParallel,
                                 String actualOutputDir) {
        log.info("Executing requests in parallel with max {} threads", maxParallel);
        
        ExecutorService executor = Executors.newFixedThreadPool(maxParallel);
        List<Future<BatchExecutionResult.RequestResult>> futures = new ArrayList<>();
        
        // Submit all requests
        for (ValidationRequest request : batchRequest.getRequests()) {
            Future<BatchExecutionResult.RequestResult> future = executor.submit(() -> {
                return executeRequest(request, batchRequest, actualOutputDir);
            });
            futures.add(future);
        }
        
        // Collect results
        for (int i = 0; i < futures.size(); i++) {
            try {
                BatchExecutionResult.RequestResult requestResult = futures.get(i).get();
                result.addRequestResult(requestResult);
                
                if (!requestResult.isSuccess() && !continueOnError) {
                    log.warn("Request failed and continueOnError=false, cancelling remaining requests");
                    // Cancel remaining futures
                    for (int j = i + 1; j < futures.size(); j++) {
                        futures.get(j).cancel(true);
                    }
                    break;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error collecting result for request {}", i, e);
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.warn("Executor shutdown interrupted", e);
        }
    }
    
    /**
     * Execute a single validation request
     */
    private BatchExecutionResult.RequestResult executeRequest(ValidationRequest request,
                                                              BatchValidationRequest batchRequest,
                                                              String actualOutputDir) {
        BatchExecutionResult.RequestResult result = new BatchExecutionResult.RequestResult();
        result.setRequestName(request.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing request: {}", request.getName());
            
            // Apply global settings if not overridden
            applyGlobalSettings(request, batchRequest, actualOutputDir);
            
            // Load validation config
            ValidationConfig validationConfig = loadValidationConfig(request);
            
            // Execute based on type
            if ("baseline-comparison".equals(request.getType())) {
                executeBaselineComparison(request, validationConfig, result, batchRequest);
            } else {
                executeNamespaceComparison(request, validationConfig, result, batchRequest);
            }
            
            result.setSuccess(true);
            log.info("Request '{}' completed successfully", request.getName());
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTrace(e));
            log.error("Request '{}' failed: {}", request.getName(), e.getMessage(), e);
        } finally {
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    /**
     * Apply global settings to a request if not already set
     */
    private void applyGlobalSettings(ValidationRequest request, BatchValidationRequest batchRequest, String actualOutputDir) {
        if (batchRequest.getSettings() == null) {
            return;
        }
        
        BatchValidationRequest.GlobalSettings settings = batchRequest.getSettings();
        
        if (request.getDefaultCluster() == null && settings.getDefaultCluster() != null) {
            request.setDefaultCluster(settings.getDefaultCluster());
        }
        
        if (request.getConfigFile() == null && settings.getDefaultConfigFile() != null) {
            request.setConfigFile(settings.getDefaultConfigFile());
        }
        
        // Apply actual output directory (with timestamp) to output path
        if (request.getOutput() != null) {
            String outputPath = Paths.get(actualOutputDir, request.getOutput()).toString();
            request.setOutput(outputPath);
        }
    }
    
    /**
     * Load validation config for a request
     */
    private ValidationConfig loadValidationConfig(ValidationRequest request) throws Exception {
        if (request.getConfigFile() != null) {
            return configLoader.load(request.getConfigFile());
        } else {
            return configLoader.load(null); // Use default
        }
    }
    
    /**
     * Execute baseline comparison
     */
    private void executeBaselineComparison(ValidationRequest request,
                                          ValidationConfig validationConfig,
                                          BatchExecutionResult.RequestResult result,
                                          BatchValidationRequest batchRequest) throws Exception {
        
        System.out.println("📂 Loading baseline from: " + request.getBaseline());
        
        // Load baseline
        YamlDataCollector yamlCollector = new YamlDataCollector();
        FlatNamespaceModel baselineModel = yamlCollector.collectFromYaml(
                request.getBaseline(), "baseline");
        
        System.out.printf("   ✓ Loaded %d objects from baseline%n", baselineModel.getObjects().size());
        System.out.println();
        
        // Collect from namespaces
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        namespaceModels.add(baselineModel);
        
        for (String nsArg : request.getNamespaces()) {
            FlatNamespaceModel nsModel = collectNamespaceData(nsArg, request);
            namespaceModels.add(nsModel);
        }
        
        // Perform comparisons and generate report
        performComparisonsAndGenerateReport(namespaceModels, validationConfig, request, result, batchRequest);
    }
    
    /**
     * Execute namespace comparison
     */
    private void executeNamespaceComparison(ValidationRequest request,
                                           ValidationConfig validationConfig,
                                           BatchExecutionResult.RequestResult result,
                                           BatchValidationRequest batchRequest) throws Exception {
        
        // Collect from all namespaces
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        
        for (String nsArg : request.getNamespaces()) {
            FlatNamespaceModel nsModel = collectNamespaceData(nsArg, request);
            namespaceModels.add(nsModel);
        }
        
        // Perform comparisons and generate report
        performComparisonsAndGenerateReport(namespaceModels, validationConfig, request, result, batchRequest);
    }
    
    /**
     * Collect namespace data from Kubernetes
     */
    private FlatNamespaceModel collectNamespaceData(String namespaceArg, 
                                                    ValidationRequest request) throws Exception {
        // Parse cluster/namespace format
        String clusterName = request.getDefaultCluster() != null ? request.getDefaultCluster() : "current";
        String namespaceName = namespaceArg;
        
        if (namespaceArg.contains("/")) {
            String[] parts = namespaceArg.split("/", 2);
            clusterName = parts[0];
            namespaceName = parts[1];
        }
        
        System.out.printf("  ⚙️  Collecting: %s/%s%n", clusterName, namespaceName);
        
        KubernetesClient client = clusterManager.getClient(clusterName);
        K8sDataCollector collector = new K8sDataCollector(client);
        
        FlatNamespaceModel model;
        if (request.getKinds() != null && !request.getKinds().isEmpty()) {
            model = collector.collectNamespaceByKinds(namespaceName, clusterName, request.getKinds());
        } else {
            model = collector.collectNamespace(namespaceName, clusterName);
        }
        
        System.out.printf("     ✓ Collected %d objects%n", model.getObjects().size());
        
        return model;
    }
    
    /**
     * Perform comparisons and generate report
     */
    private void performComparisonsAndGenerateReport(List<FlatNamespaceModel> namespaceModels,
                                                     ValidationConfig validationConfig,
                                                     ValidationRequest request,
                                                     BatchExecutionResult.RequestResult result,
                                                     BatchValidationRequest batchRequest) throws Exception {
        
        System.out.println();
        System.out.println("🔍 Performing comparisons...");
        
        // Perform pairwise comparisons
        Map<String, NamespaceComparison> comparisons = new HashMap<>();
        int totalDifferences = 0;
        int totalObjects = 0;
        
        // Check if V2 semantic comparison is enabled via batch settings
        boolean useSemanticV2 = batchRequest != null && 
                                batchRequest.getSettings() != null && 
                                batchRequest.getSettings().isUseSemanticV2();
        
        for (int i = 0; i < namespaceModels.size() - 1; i++) {
            for (int j = i + 1; j < namespaceModels.size(); j++) {
                FlatNamespaceModel left = namespaceModels.get(i);
                FlatNamespaceModel right = namespaceModels.get(j);
                
                String compKey = left.getClusterName() + "/" + left.getName() + 
                               "_vs_" + right.getClusterName() + "/" + right.getName();
                
                NamespaceComparison comparison;
                if (useSemanticV2) {
                    log.info("[V2] Using semantic comparison for batch: {}", compKey);
                    comparison = ValidationServiceV2.compareFlat(
                            left, right,
                            left.getClusterName() + "/" + left.getName(),
                            right.getClusterName() + "/" + right.getName(),
                            validationConfig
                    );
                } else {
                    comparison = NamespaceComparator.compareNamespace(
                            left.getObjects(), right.getObjects(),
                            left.getClusterName() + "/" + left.getName(),
                            right.getClusterName() + "/" + right.getName(),
                            validationConfig
                    );
                }
                
                comparisons.put(compKey, comparison);
                
                // Count differences
                totalObjects += comparison.getObjectComparisons().size();
                totalDifferences += comparison.getSummary().getDifferencesCount();
            }
        }
        
        result.setObjectsCompared(totalObjects);
        result.setDifferencesFound(totalDifferences);
        
        System.out.printf("   ✓ Found %d differences across %d objects%n", totalDifferences, totalObjects);
        
        // Store execution data for batch summary report
        requestExecutionData.put(request.getName(), 
                new BatchSummaryReportGenerator.RequestExecutionData(
                        namespaceModels, comparisons, request));
        
        // Generate Excel report if output specified
        if (request.getOutput() != null) {
            System.out.println();
            System.out.println("📊 Generating Excel report...");
            
            ExcelReportGenerator reportGenerator = new ExcelReportGenerator();
            reportGenerator.generateReport(namespaceModels, comparisons, request.getOutput(), validationConfig);
            
            result.setOutputPath(request.getOutput());
            System.out.println("   ✓ Report saved to: " + request.getOutput());
        }
        
        // Display verbose output if requested
        if (request.isVerbose()) {
            displayVerboseResults(comparisons);
        }
    }
    
    /**
     * Display verbose comparison results
     */
    private void displayVerboseResults(Map<String, NamespaceComparison> comparisons) {
        System.out.println();
        System.out.println("📋 Detailed Results:");
        System.out.println();
        
        for (Map.Entry<String, NamespaceComparison> entry : comparisons.entrySet()) {
            System.out.println("┌─ " + entry.getKey());
            System.out.printf("│  Total objects: %d%n", entry.getValue().getObjectComparisons().size());
            System.out.printf("│  Differences: %d%n", entry.getValue().getSummary().getDifferencesCount());
            System.out.println("└─────────────────────────────────────────");
        }
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Close resources
     */
    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
