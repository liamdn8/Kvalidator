package com.nfv.validator.cli;

import com.nfv.validator.batch.BatchExecutor;
import com.nfv.validator.batch.BatchRequestLoader;
import com.nfv.validator.comparison.NamespaceComparator;
import com.nfv.validator.config.ConfigLoader;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollector;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import com.nfv.validator.model.batch.BatchExecutionResult;
import com.nfv.validator.model.batch.BatchValidationRequest;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.report.ExcelReportGenerator;
import com.nfv.validator.yaml.YamlDataCollector;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.util.*;

/**
 * Command Line Interface handler for KValidator
 * 
 * Usage:
 *   java -jar kvalidator.jar [cluster-name/]namespace1 [cluster-name/]namespace2 ...
 * 
 * Examples:
 *   java -jar kvalidator.jar app-dev app-prod
 *   java -jar kvalidator.jar cluster1/app-dev cluster2/app-dev
 */
@Slf4j
public class CommandLineInterface {

    private Options options;
    private KubernetesClusterManager clusterManager;
    private ValidationConfig validationConfig;

    public CommandLineInterface() {
        initializeOptions();
        this.clusterManager = new KubernetesClusterManager();
    }

    private void initializeOptions() {
        options = new Options();

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Display help information")
                .build());
        
        options.addOption(Option.builder("r")
                .longOpt("request-file")
                .hasArg()
                .argName("request-file")
                .desc("Path to batch validation request file (JSON or YAML)")
                .build());
        
        options.addOption(Option.builder("c")
                .longOpt("cluster")
                .hasArg()
                .argName("cluster-name")
                .desc("Default cluster name (default: current context)")
                .build());
        
        options.addOption(Option.builder("k")
                .longOpt("kinds")
                .hasArg()
                .argName("kind1,kind2,...")
                .desc("Resource kinds to compare (default: all)")
                .build());
        
        options.addOption(Option.builder("v")
                .longOpt("verbose")
                .desc("Show detailed comparison results")
                .build());
        
        options.addOption(Option.builder("f")
                .longOpt("config")
                .hasArg()
                .argName("config-file")
                .desc("Path to validation config file (default: ./validation-config.yaml)")
                .build());
        
        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("excel-file")
                .desc("Export comparison results to Excel file (e.g., report.xlsx)")
                .build());
        
        options.addOption(Option.builder("b")
                .longOpt("baseline")
                .hasArg()
                .argName("yaml-path")
                .desc("Path to baseline YAML file or directory (design/expected state)")
                .build());
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            printHelp();
            return;
        }

        // Check for batch mode
        if (cmd.hasOption("r")) {
            executeBatchMode(cmd.getOptionValue("r"));
            return;
        }

        String defaultCluster = cmd.getOptionValue("c", "current");
        String[] kinds = cmd.hasOption("k") ? cmd.getOptionValue("k").split(",") : null;
        boolean verbose = cmd.hasOption("v");
        String configFile = cmd.getOptionValue("f");
        String excelOutput = cmd.getOptionValue("o");
        String baselinePath = cmd.getOptionValue("b");
        
        // Load validation config
        ConfigLoader configLoader = new ConfigLoader();
        this.validationConfig = configLoader.load(configFile);
        
        if (this.validationConfig.getIgnoreFields() != null && !this.validationConfig.getIgnoreFields().isEmpty()) {
            System.out.println("📋 Loaded " + this.validationConfig.getIgnoreFields().size() + " ignore fields from config");
            System.out.println();
        }

        List<String> namespaceArgs = cmd.getArgList();
        
        // Validate arguments based on mode
        if (baselinePath != null) {
            // Baseline mode: can compare 1+ namespaces against baseline
            if (namespaceArgs.isEmpty()) {
                System.err.println("Error: At least 1 namespace required when using baseline comparison");
                System.err.println("Usage: java -jar kvalidator.jar -b <baseline-path> namespace1 [namespace2 ...]");
                System.exit(1);
            }
        } else {
            // Normal mode: need at least 2 namespaces
            if (namespaceArgs.size() < 2) {
                System.err.println("Error: At least 2 namespaces required for comparison");
                System.err.println("Usage: java -jar kvalidator.jar namespace1 namespace2 [namespace3 ...]");
                System.exit(1);
            }
        }

        compareNamespaces(namespaceArgs, defaultCluster, kinds, verbose, excelOutput, baselinePath);
    }
    
    /**
     * Execute batch mode from request file
     */
    private void executeBatchMode(String requestFilePath) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║       KValidator - Batch Validation Mode                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        System.out.println("📁 Loading batch request from: " + requestFilePath);
        
        // Load batch request
        BatchRequestLoader loader = new BatchRequestLoader();
        BatchValidationRequest batchRequest = loader.load(requestFilePath);
        
        System.out.printf("   ✓ Loaded %d validation requests%n", batchRequest.getRequests().size());
        
        if (batchRequest.getDescription() != null) {
            System.out.println("   Description: " + batchRequest.getDescription());
        }
        
        System.out.println();
        
        // Execute batch
        BatchExecutor executor = new BatchExecutor();
        BatchExecutionResult result = executor.execute(batchRequest);
        
        // Print summary
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   Batch Execution Summary                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("Total Requests:      %d%n", result.getTotalRequests());
        System.out.printf("✅ Successful:       %d%n", result.getSuccessfulRequests());
        System.out.printf("❌ Failed:           %d%n", result.getFailedRequests());
        System.out.println();
        
        // Print individual results
        System.out.println("Individual Results:");
        System.out.println("─────────────────────────────────────────────────────────────────");
        
        for (BatchExecutionResult.RequestResult reqResult : result.getRequestResults()) {
            String status = reqResult.isSuccess() ? "✅ SUCCESS" : "❌ FAILED";
            System.out.printf("%-40s %s%n", reqResult.getRequestName(), status);
            
            if (reqResult.isSuccess()) {
                if (reqResult.getOutputPath() != null) {
                    System.out.printf("   Output: %s%n", reqResult.getOutputPath());
                }
                System.out.printf("   Objects compared: %d, Differences: %d%n", 
                        reqResult.getObjectsCompared(), reqResult.getDifferencesFound());
                System.out.printf("   Execution time: %.2f seconds%n", reqResult.getExecutionTimeMs() / 1000.0);
            } else {
                System.out.printf("   Error: %s%n", reqResult.getErrorMessage());
            }
            System.out.println();
        }
        
        System.out.println("─────────────────────────────────────────────────────────────────");
        
        // Exit with appropriate code
        if (!result.isAllSuccessful()) {
            System.exit(1);
        }
    }

    private void compareNamespaces(List<String> namespaceArgs, String defaultCluster, 
                                   String[] kinds, boolean verbose, String excelOutput, String baselinePath) throws Exception {
        
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║       KValidator - NFV Infrastructure Comparison Tool            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Load baseline if specified
        FlatNamespaceModel baselineModel = null;
        if (baselinePath != null) {
            System.out.println("📂 Loading baseline from: " + baselinePath);
            try {
                YamlDataCollector yamlCollector = new YamlDataCollector();
                baselineModel = yamlCollector.collectFromYaml(baselinePath, "baseline");
                System.out.printf("   ✓ Loaded %d objects from baseline%n", baselineModel.getObjects().size());
                System.out.println();
            } catch (Exception e) {
                System.err.println("❌ Failed to load baseline: " + e.getMessage());
                System.exit(1);
            }
        }

        // Parse namespace arguments (cluster/namespace or just namespace)
        List<NamespaceTarget> targets = new ArrayList<>();
        for (String arg : namespaceArgs) {
            NamespaceTarget target = parseNamespaceArg(arg, defaultCluster);
            targets.add(target);
        }

        // Collect data from all namespaces
        System.out.println("📊 Collecting data from namespaces...");
        System.out.println();
        
        List<FlatNamespaceModel> namespaceModels = new ArrayList<>();
        
        // Add baseline as first model if present
        if (baselineModel != null) {
            namespaceModels.add(baselineModel);
        }
        
        for (NamespaceTarget target : targets) {
            System.out.printf("  ⚙️  Collecting: %s/%s%n", target.clusterName, target.namespaceName);
            
            KubernetesClient client = clusterManager.getClient(target.clusterName);
            K8sDataCollector collector = new K8sDataCollector(client);
            
            FlatNamespaceModel model;
            if (kinds != null && kinds.length > 0) {
                model = collector.collectNamespaceByKinds(target.namespaceName, target.clusterName, Arrays.asList(kinds));
            } else {
                model = collector.collectNamespace(target.namespaceName, target.clusterName);
            }
            
            namespaceModels.add(model);
            System.out.printf("     ✓ Collected %d objects%n", model.getObjects().size());
        }
        
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        // Compare based on mode
        System.out.println("🔍 Comparison Results:");
        System.out.println();
        
        Map<String, NamespaceComparison> comparisons = new LinkedHashMap<>();

        if (baselineModel != null) {
            // Baseline mode: compare all namespaces against baseline
            for (int i = 1; i < namespaceModels.size(); i++) {
                FlatNamespaceModel ns = namespaceModels.get(i);
                
                NamespaceComparison comparison = NamespaceComparator.compareNamespace(
                    baselineModel.getObjects(), ns.getObjects(), 
                    baselineModel.getName(), ns.getName(), validationConfig
                );
                
                String compKey = baselineModel.getName() + "_vs_" + ns.getName();
                comparisons.put(compKey, comparison);
                
                printComparisonResult(baselineModel, ns, comparison, verbose);
            }
        } else {
            // Normal mode: compare all namespaces pairwise
            for (int i = 0; i < namespaceModels.size(); i++) {
                for (int j = i + 1; j < namespaceModels.size(); j++) {
                    FlatNamespaceModel ns1 = namespaceModels.get(i);
                    FlatNamespaceModel ns2 = namespaceModels.get(j);
                    
                    NamespaceComparison comparison = NamespaceComparator.compareNamespace(
                        ns1.getObjects(), ns2.getObjects(), ns1.getName(), ns2.getName(), validationConfig
                    );
                    
                    String compKey = ns1.getName() + "_vs_" + ns2.getName();
                    comparisons.put(compKey, comparison);
                
                    printComparisonResult(ns1, ns2, comparison, verbose);
                }
            }
        }
        
        // Export to Excel if requested
        if (excelOutput != null && !excelOutput.isEmpty()) {
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("📄 Exporting results to Excel...");
            
            try {
                ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
                excelGenerator.generateReport(namespaceModels, comparisons, excelOutput, this.validationConfig);
                System.out.println("✅ Excel report generated: " + excelOutput);
            } catch (Exception e) {
                log.error("Failed to generate Excel report", e);
                System.err.println("❌ Failed to generate Excel report: " + e.getMessage());
            }
        }

        clusterManager.closeAll();
    }

    private NamespaceTarget parseNamespaceArg(String arg, String defaultCluster) {
        if (arg.contains("/")) {
            String[] parts = arg.split("/", 2);
            return new NamespaceTarget(parts[0], parts[1]);
        } else {
            return new NamespaceTarget(defaultCluster, arg);
        }
    }

    private void printComparisonResult(FlatNamespaceModel ns1, FlatNamespaceModel ns2, 
                                      NamespaceComparison comparison, boolean verbose) {
        String label1 = ns1.getClusterName() + "/" + ns1.getName();
        String label2 = ns2.getClusterName() + "/" + ns2.getName();
        
        System.out.printf("┌─ Comparing: %s ↔ %s%n", label1, label2);
        
        // Summary
        NamespaceComparison.ComparisonSummary summary = comparison.getSummary();
        System.out.printf("│  Objects: %d vs %d (only in left: %d, only in right: %d, common: %d)%n",
            summary.getTotalInLeft(), summary.getTotalInRight(),
            summary.getOnlyInLeft(), summary.getOnlyInRight(),
            summary.getCommonObjects());
        
        System.out.printf("│  Match Rate: %.1f%% (%d matched / %d compared)%n",
            summary.getMatchPercentage(), summary.getMatchedObjects(), summary.getCommonObjects());
        
        System.out.printf("│  Differences: %d objects with differences%n", summary.getDifferencesCount());
        
        if (verbose && summary.getDifferencesCount() > 0) {
            System.out.println("│");
            System.out.println("│  📋 Detailed Differences:");
            
            Map<String, List<ObjectComparison>> byType = comparison.getObjectsByType();
            for (Map.Entry<String, List<ObjectComparison>> entry : byType.entrySet()) {
                String type = entry.getKey();
                List<ObjectComparison> objects = entry.getValue();
                
                List<ObjectComparison> withDiffs = objects.stream()
                    .filter(obj -> obj.getDifferenceCount() > 0)
                    .collect(java.util.stream.Collectors.toList());
                
                if (!withDiffs.isEmpty()) {
                    System.out.printf("│     %s (%d objects with diffs):%n", type, withDiffs.size());
                    
                    for (ObjectComparison obj : withDiffs) {
                        System.out.printf("│       • %s: %d differences%n", 
                            obj.getObjectId(), obj.getDifferenceCount());
                        
                        if (verbose) {
                            obj.getDifferences().forEach(diff -> {
                                System.out.printf("│           - %s: [%s] ≠ [%s]%n",
                                    diff.getKey(),
                                    diff.getLeftValue() != null ? diff.getLeftValue() : "null",
                                    diff.getRightValue() != null ? diff.getRightValue() : "null");
                            });
                        }
                    }
                }
            }
        }
        
        System.out.println("└─────────────────────────────────────────────────────────────────");
        System.out.println();
    }

    private void printHelp() {
        System.out.println("KValidator - NFV Infrastructure Validation Tool");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  # Single validation mode");
        System.out.println("  java -jar kvalidator.jar [OPTIONS] namespace1 namespace2 [namespace3 ...]");
        System.out.println("  java -jar kvalidator.jar [OPTIONS] -b <baseline-path> namespace1 [namespace2 ...]");
        System.out.println();
        System.out.println("  # Batch validation mode");
        System.out.println("  java -jar kvalidator.jar -r <request-file>");
        System.out.println();
        System.out.println("ARGUMENTS:");
        System.out.println("  namespaceN              Namespace to compare. Format: [cluster-name/]namespace");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("  -h, --help              Display this help message");
        System.out.println("  -r, --request-file FILE Path to batch validation request file (JSON or YAML)");
        System.out.println("                          Run multiple validations from a single file");
        System.out.println("  -b, --baseline PATH     Path to baseline YAML file or directory");
        System.out.println("                          Compare namespaces against design/expected state");
        System.out.println("  -c, --cluster NAME      Default cluster name (default: current context)");
        System.out.println("  -k, --kinds KIND1,...   Resource kinds to compare (default: all)");
        System.out.println("                          Examples: Deployment,Service,ConfigMap");
        System.out.println("  -v, --verbose           Show detailed comparison results");
        System.out.println("  -f, --config FILE       Path to validation config file");
        System.out.println("                          (default: ./validation-config.yaml)");
        System.out.println("  -o, --output FILE       Export comparison results to Excel file");
        System.out.println("                          (e.g., report.xlsx)");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  # Single validation - Compare two namespaces in current cluster");
        System.out.println("  java -jar kvalidator.jar app-dev app-prod");
        System.out.println();
        System.out.println("  # Single validation - Compare namespaces against baseline design");
        System.out.println("  java -jar kvalidator.jar -b baseline.yaml app-dev app-staging app-prod");
        System.out.println();
        System.out.println("  # Batch validation - Run multiple validations from request file");
        System.out.println("  java -jar kvalidator.jar -r validation-request.yaml");
        System.out.println();
        System.out.println("  # Compare against baseline directory and export");
        System.out.println("  java -jar kvalidator.jar -b design/ -o report.xlsx app-dev app-prod");
        System.out.println();
        System.out.println("  # Compare and export to Excel");
        System.out.println("  java -jar kvalidator.jar -o report.xlsx app-dev app-staging app-prod");
        System.out.println();
        System.out.println("  # Compare namespaces from different clusters");
        System.out.println("  java -jar kvalidator.jar cluster1/app-dev cluster2/app-dev");
        System.out.println();
        System.out.println("  # Compare specific resource kinds only");
        System.out.println("  java -jar kvalidator.jar -k Deployment,Service app-dev app-prod");
        System.out.println();
        System.out.println("  # Verbose output with detailed differences");
        System.out.println("  java -jar kvalidator.jar -v app-dev app-staging app-prod");
        System.out.println();
    }
    


    private static class NamespaceTarget {
        String clusterName;
        String namespaceName;

        NamespaceTarget(String clusterName, String namespaceName) {
            this.clusterName = clusterName;
            this.namespaceName = namespaceName;
        }
    }
}
