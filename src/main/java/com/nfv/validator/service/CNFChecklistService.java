package com.nfv.validator.service;

import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.batch.BatchValidationRequest;
import com.nfv.validator.model.batch.ValidationRequest;
import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.model.cnf.CNFChecklistRequest;
import com.nfv.validator.model.comparison.CnfComparison;
import com.nfv.validator.model.comparison.ComparisonStatus;
import com.nfv.validator.model.comparison.KeyComparison;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import com.nfv.validator.config.ValidationConfig;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to convert CNF Checklist requests to standard validation format
 */
@Slf4j
@ApplicationScoped
public class CNFChecklistService {
    
    /**
     * Auto-convert CNF checklist request to use identity-based matching when applicable.
     * DISABLED: This auto-conversion is too complex and error-prone for nested arrays.
     * Users should explicitly use identity format (e.g., env[DB_HOST].name) if they want identity matching,
     * or use numeric indices (e.g., env[0].name) with "value" strategy.
     * 
     * @param request Original CNF checklist request
     * @return Request unchanged (auto-conversion disabled)
     */
    public CNFChecklistRequest autoConvertToIdentityMatching(CNFChecklistRequest request) {
        // Auto-conversion disabled - too error-prone
        // Users must explicitly format their requests
        log.debug("Auto-conversion disabled. Using strategy: {}", request.getMatchingStrategy());
        return request;
    }
    
    /**
     * Convert CNF checklist request to baseline namespace model
     * Groups items by vimName/namespace and creates baseline objects with expected values
     * 
     * @param request CNF checklist request with items to validate
     * @return Map of "vimName/namespace" to FlatNamespaceModel containing baseline objects
     */
    public Map<String, FlatNamespaceModel> convertToBaseline(CNFChecklistRequest request) {
        log.info("Converting CNF checklist with {} items to baseline format", request.getItems().size());
        
        Map<String, FlatNamespaceModel> namespaceMap = new HashMap<>();
        
        for (CNFChecklistItem item : request.getItems()) {
            // Create unique key: vimName/namespace
            String namespaceKey = item.getVimName() + "/" + item.getNamespace();
            
            // Get or create namespace model
            FlatNamespaceModel namespace = namespaceMap.computeIfAbsent(namespaceKey, key -> {
                FlatNamespaceModel model = new FlatNamespaceModel();
                model.setName(item.getNamespace());
                model.setClusterName(item.getVimName());
                model.setObjects(new HashMap<>());
                return model;
            });
            
            // Create object key using just the name (matching K8sDataCollector format)
            // Note: Within a namespace, K8s object names are unique per kind, so using just name is safe
            // when filtering by specific kinds
            String objectKey = item.getObjectName();
            
            // Get or create object model
            FlatObjectModel object = namespace.getObjects().computeIfAbsent(objectKey, key -> {
                FlatObjectModel model = new FlatObjectModel();
                model.setKind(item.getKind());
                model.setName(item.getObjectName());
                model.setNamespace(item.getNamespace());
                model.setApiVersion("v1"); // Default, will be overridden by actual object
                model.setMetadata(new HashMap<>());
                model.setSpec(new HashMap<>());
                return model;
            });
            
            // Add the field to appropriate section
            addFieldToObject(object, item.getFieldKey(), item.getManoValue());
        }
        
        // Log summary
        for (Map.Entry<String, FlatNamespaceModel> entry : namespaceMap.entrySet()) {
            log.info("Created baseline for namespace '{}' with {} objects", 
                entry.getKey(), entry.getValue().getObjects().size());
        }
        
        return namespaceMap;
    }
    
    /**
     * Add a field to the appropriate section (metadata or spec) of the object
     * 
     * @param object FlatObjectModel to add field to
     * @param fieldKey Field path (e.g., "spec.replicas", "metadata.labels.app")
     * @param value Field value
     */
    private void addFieldToObject(FlatObjectModel object, String fieldKey, String value) {
        // Determine if field belongs to metadata or spec
        if (fieldKey.startsWith("metadata.")) {
            // Remove "metadata." prefix and add to metadata map
            String metadataKey = fieldKey.substring("metadata.".length());
            object.addMetadata(metadataKey, value);
            log.debug("Added metadata field: {} = {}", metadataKey, value);
        } else if (fieldKey.startsWith("spec.")) {
            // Remove "spec." prefix and add to spec map
            String specKey = fieldKey.substring("spec.".length());
            object.addSpec(specKey, value);
            log.debug("Added spec field: {} = {}", specKey, value);
        } else if (fieldKey.startsWith("data.")) {
            // For ConfigMap data fields, treat as spec
            object.addSpec(fieldKey, value);
            log.debug("Added data field: {} = {}", fieldKey, value);
        } else {
            // Default: add to spec if not explicitly metadata
            object.addSpec(fieldKey, value);
            log.debug("Added field to spec (default): {} = {}", fieldKey, value);
        }
    }
    
    /**
     * Get list of unique vimName/namespace combinations from CNF checklist request
     * 
     * @param request CNF checklist request
     * @return List of unique "vimName/namespace" strings
     */
    public java.util.List<String> extractNamespaces(CNFChecklistRequest request) {
        return request.getItems().stream()
            .map(item -> item.getVimName() + "/" + item.getNamespace())
            .distinct()
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get list of unique kinds from CNF checklist request
     * 
     * @param request CNF checklist request
     * @return List of unique Kubernetes kinds
     */
    public java.util.List<String> extractKinds(CNFChecklistRequest request) {
        return request.getItems().stream()
            .map(CNFChecklistItem::getKind)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Convert CNF Checklist request to Batch Validation request using flattened approach
     * Groups items by vimName/namespace and creates separate validation requests
     * Uses FlatNamespaceModel directly instead of YAML files for better performance
     * 
     * @param cnfRequest CNF checklist request with items to validate
     * @return BatchValidationRequest that can be executed by batch validator
     */
    public BatchValidationRequest convertToBatchRequestFlattened(CNFChecklistRequest cnfRequest) {
        log.info("Converting CNF checklist with {} items to batch validation format (flattened approach)", 
                cnfRequest.getItems().size());
        
        // Group items by vimName/namespace
        Map<String, List<CNFChecklistItem>> itemsByNamespace = new LinkedHashMap<>();
        for (CNFChecklistItem item : cnfRequest.getItems()) {
            String key = item.getVimName() + "/" + item.getNamespace();
            itemsByNamespace.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        
        // Create a validation request for each namespace
        List<ValidationRequest> validationRequests = new ArrayList<>();
        
        for (Map.Entry<String, List<CNFChecklistItem>> entry : itemsByNamespace.entrySet()) {
            String namespaceKey = entry.getKey();
            List<CNFChecklistItem> items = entry.getValue();
            
            String[] parts = namespaceKey.split("/");
            String vimName = parts[0];
            String namespace = parts[1];
            
            // Create flattened baseline model
            Map<String, FlatNamespaceModel> baselineModel = createFlattenedBaseline(items, vimName, namespace);
            
            // Extract unique kinds for this namespace
            List<String> kinds = items.stream()
                    .map(CNFChecklistItem::getKind)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            
            // Create validation request
            ValidationRequest validationRequest = new ValidationRequest();
            validationRequest.setName(String.format("%s/%s (%d fields)", vimName, namespace, items.size()));
            validationRequest.setType("flatten-comparison");
            validationRequest.setNamespaces(java.util.Arrays.asList(namespace));
            validationRequest.setKinds(kinds);
            validationRequest.setDefaultCluster(vimName);
            validationRequest.setVerbose(false);
            
            // Transfer ignore fields from CNF request
            if (cnfRequest.getIgnoreFields() != null) {
                validationRequest.setIgnoreFields(new ArrayList<>(cnfRequest.getIgnoreFields()));
            }
            
            // Store flattened baseline in request metadata for validator to use
            validationRequest.setFlattenedBaseline(baselineModel);
            
            validationRequests.add(validationRequest);
            
            log.info("Created flattened validation request for {}: {} items, {} kinds", 
                    namespaceKey, items.size(), kinds.size());
        }
        
        // Create batch request
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        batchRequest.setVersion("1.0");
        batchRequest.setDescription(cnfRequest.getDescription() != null ? 
                cnfRequest.getDescription() : "CNF Checklist Validation (Flattened)");
        batchRequest.setRequests(validationRequests);
        
        // Set global settings
        BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
        settings.setContinueOnError(true);
        settings.setGenerateSummaryReport(true);
        settings.setUseSemanticV2(cnfRequest.shouldUseSemanticV2()); // Auto-determine from matchingStrategy
        settings.setMatchingStrategy(cnfRequest.getMatchingStrategy()); // Pass matchingStrategy to batch
        batchRequest.setSettings(settings);
        
        // Store original CNF request for later use (flexible matching in convertToCnfComparison)
        batchRequest.setCnfChecklistRequest(cnfRequest);
        
        log.info("Converted CNF checklist to batch request with {} validation requests (flattened approach), matchingStrategy={}, useSemanticV2={}", 
                validationRequests.size(), cnfRequest.getMatchingStrategy(), cnfRequest.shouldUseSemanticV2());
        
        return batchRequest;
    }

    /**
     * Convert CNF Checklist request to Batch Validation request
     * Groups items by vimName/namespace and creates separate validation requests
     * Each request compares baseline (expected values) vs runtime
     * 
     * @param cnfRequest CNF checklist request with items to validate
     * @return BatchValidationRequest that can be executed by batch validator
     */
    public BatchValidationRequest convertToBatchRequest(CNFChecklistRequest cnfRequest) {
        log.info("Converting CNF checklist with {} items to batch validation format", 
                cnfRequest.getItems().size());
        
        // Group items by vimName/namespace
        Map<String, List<CNFChecklistItem>> itemsByNamespace = new LinkedHashMap<>();
        for (CNFChecklistItem item : cnfRequest.getItems()) {
            String key = item.getVimName() + "/" + item.getNamespace();
            itemsByNamespace.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        
        // Create a validation request for each namespace
        List<ValidationRequest> validationRequests = new ArrayList<>();
        
        for (Map.Entry<String, List<CNFChecklistItem>> entry : itemsByNamespace.entrySet()) {
            String namespaceKey = entry.getKey();
            List<CNFChecklistItem> items = entry.getValue();
            
            String[] parts = namespaceKey.split("/");
            String vimName = parts[0];
            String namespace = parts[1];
            
            // Create baseline YAML content from checklist items
            String baselineYaml = generateBaselineYaml(items, vimName, namespace);
            
            // Create temporary baseline file
            try {
                File tempBaselineFile = File.createTempFile("cnf-baseline-" + vimName + "-" + namespace + "-", ".yaml");
                tempBaselineFile.deleteOnExit();
                Files.write(tempBaselineFile.toPath(), baselineYaml.getBytes());
                
                // Extract unique kinds for this namespace
                List<String> kinds = items.stream()
                        .map(CNFChecklistItem::getKind)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
                
                // Create validation request
                ValidationRequest validationRequest = new ValidationRequest();
                validationRequest.setName(String.format("%s/%s (%d fields)", vimName, namespace, items.size()));
                validationRequest.setType("baseline-comparison");
                validationRequest.setNamespaces(java.util.Arrays.asList(namespace)); // Only actual namespace, not vimName/namespace
                validationRequest.setBaseline(tempBaselineFile.getAbsolutePath());
                validationRequest.setKinds(kinds);
                validationRequest.setDefaultCluster(vimName);
                validationRequest.setVerbose(false);
                
                validationRequests.add(validationRequest);
                
                log.info("Created validation request for {}: {} items, {} kinds", 
                        namespaceKey, items.size(), kinds.size());
                
            } catch (IOException e) {
                log.error("Failed to create baseline file for {}", namespaceKey, e);
                throw new RuntimeException("Failed to create baseline file: " + e.getMessage(), e);
            }
        }
        
        // Create batch request
        BatchValidationRequest batchRequest = new BatchValidationRequest();
        batchRequest.setVersion("1.0");
        batchRequest.setDescription(cnfRequest.getDescription() != null ? 
                cnfRequest.getDescription() : "CNF Checklist Validation");
        batchRequest.setRequests(validationRequests);
        
        // Set global settings
        BatchValidationRequest.GlobalSettings settings = new BatchValidationRequest.GlobalSettings();
        settings.setContinueOnError(true);
        settings.setGenerateSummaryReport(true);
        batchRequest.setSettings(settings);
        
        log.info("Converted CNF checklist to batch request with {} validation requests", 
                validationRequests.size());
        
        return batchRequest;
    }
    
    /**
     * Generate baseline YAML content from CNF checklist items
     * Creates Kubernetes YAML with only the fields specified in the checklist
     * 
     * @param items List of checklist items for a specific namespace
     * @param vimName VIM/cluster name
     * @param namespace Kubernetes namespace
     * @return YAML string content
     */
    private String generateBaselineYaml(List<CNFChecklistItem> items, String vimName, String namespace) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Auto-generated baseline from CNF Checklist\n");
        yaml.append("# VIM: ").append(vimName).append("\n");
        yaml.append("# Namespace: ").append(namespace).append("\n");
        yaml.append("---\n");
        
        // Group items by object (kind/name)
        Map<String, List<CNFChecklistItem>> itemsByObject = new LinkedHashMap<>();
        for (CNFChecklistItem item : items) {
            String objectKey = item.getKind() + "/" + item.getObjectName();
            itemsByObject.computeIfAbsent(objectKey, k -> new ArrayList<>()).add(item);
        }
        
        // Generate YAML for each object
        boolean first = true;
        for (Map.Entry<String, List<CNFChecklistItem>> entry : itemsByObject.entrySet()) {
            List<CNFChecklistItem> objectItems = entry.getValue();
            CNFChecklistItem firstItem = objectItems.get(0);
            
            if (!first) {
                yaml.append("---\n");
            }
            first = false;
            
            yaml.append("apiVersion: v1\n");
            yaml.append("kind: ").append(firstItem.getKind()).append("\n");
            yaml.append("metadata:\n");
            yaml.append("  name: ").append(firstItem.getObjectName()).append("\n");
            yaml.append("  namespace: ").append(namespace).append("\n");
            
            // Add fields from checklist
            boolean hasSpec = false;
            boolean hasData = false;
            StringBuilder specContent = new StringBuilder();
            StringBuilder dataContent = new StringBuilder();
            
            for (CNFChecklistItem item : objectItems) {
                String fieldKey = item.getFieldKey();
                String value = item.getManoValue();
                
                if (fieldKey.startsWith("spec.")) {
                    hasSpec = true;
                    String specField = fieldKey.substring("spec.".length());
                    appendYamlField(specContent, specField, value, "  ");
                } else if (fieldKey.startsWith("data.")) {
                    hasData = true;
                    String dataField = fieldKey.substring("data.".length());
                    appendYamlField(dataContent, dataField, value, "  ");
                } else {
                    // Default to spec
                    hasSpec = true;
                    appendYamlField(specContent, fieldKey, value, "  ");
                }
            }
            
            if (hasSpec) {
                yaml.append("spec:\n");
                yaml.append(specContent);
            }
            
            if (hasData) {
                yaml.append("data:\n");
                yaml.append(dataContent);
            }
        }
        
        return yaml.toString();
    }
    
    /**
     * Create flattened baseline model from CNF checklist items
     * Uses FlatNamespaceModel/FlatObjectModel directly instead of YAML
     * 
     * @param items List of checklist items for a specific namespace
     * @param vimName VIM/cluster name
     * @param namespace Kubernetes namespace
     * @return Map of namespace key to FlatNamespaceModel
     */
    private Map<String, FlatNamespaceModel> createFlattenedBaseline(
            List<CNFChecklistItem> items, String vimName, String namespace) {
        
        Map<String, FlatNamespaceModel> result = new HashMap<>();
        String namespaceKey = vimName + "/" + namespace;
        
        // Create namespace model
        FlatNamespaceModel namespaceModel = new FlatNamespaceModel();
        namespaceModel.setName(namespace);
        namespaceModel.setClusterName(vimName);
        namespaceModel.setObjects(new HashMap<>());
        
        // Group items by object (kind/name)
        Map<String, List<CNFChecklistItem>> itemsByObject = new LinkedHashMap<>();
        for (CNFChecklistItem item : items) {
            String objectKey = item.getObjectName();  // Use just name as key
            itemsByObject.computeIfAbsent(objectKey, k -> new ArrayList<>()).add(item);
        }
        
        // Create FlatObjectModel for each object
        for (Map.Entry<String, List<CNFChecklistItem>> entry : itemsByObject.entrySet()) {
            String objectKey = entry.getKey();
            List<CNFChecklistItem> objectItems = entry.getValue();
            CNFChecklistItem firstItem = objectItems.get(0);
            
            // Create object model
            FlatObjectModel objectModel = new FlatObjectModel();
            objectModel.setKind(firstItem.getKind());
            objectModel.setName(firstItem.getObjectName());
            objectModel.setNamespace(namespace);
            objectModel.setApiVersion("v1"); // Default
            objectModel.setMetadata(new HashMap<>());
            objectModel.setSpec(new HashMap<>());
            
            // Add fields from checklist items
            for (CNFChecklistItem item : objectItems) {
                addFieldToObject(objectModel, item.getFieldKey(), item.getManoValue());
            }
            
            namespaceModel.getObjects().put(objectKey, objectModel);
            
            log.debug("Created flattened baseline object {}/{} with {} fields", 
                    firstItem.getKind(), firstItem.getObjectName(), objectItems.size());
        }
        
        result.put(namespaceKey, namespaceModel);
        
        log.info("Created flattened baseline for {} with {} objects", 
                namespaceKey, namespaceModel.getObjects().size());
        
        return result;
    }
    
    /**
     * Append a field to YAML content with proper indentation
     * Handles nested fields (e.g., "template.spec.containers[0].image")
     */
    private void appendYamlField(StringBuilder yaml, String fieldPath, String value, String indent) {
        // Simple implementation: just add as flat key-value
        // For nested paths, we'll use the simple approach
        yaml.append(indent).append(fieldPath).append(": ");
        
        // Quote string values if they contain special characters
        if (value.matches(".*[:#@].*") || value.matches("\\d+")) {
            yaml.append("\"").append(value).append("\"");
        } else {
            yaml.append(value);
        }
        yaml.append("\n");
    }
    
    /**
     * Convert standard NamespaceComparison results to CNF-specific format
     * Filters and formats results to only show fields from the checklist

     * 
     * @param request Original CNF checklist request
     * @param comparisons Map of NamespaceComparison results
     * @return List of CnfComparison results grouped by VIM/namespace
     */
    public List<CnfComparison> convertToCnfComparison(
            CNFChecklistRequest request,
            Map<String, NamespaceComparison> comparisons) {
        
        log.info("Converting comparison results to CNF format for {} items", request.getItems().size());
        
        // Create a ValidationConfig to check ignore rules
        ValidationConfig validationConfig = new ValidationConfig();
        if (request.getIgnoreFields() != null) {
            for (String ignoreField : request.getIgnoreFields()) {
                validationConfig.addIgnoreField(ignoreField);
            }
            log.info("Loaded {} ignore rules for CNF comparison", request.getIgnoreFields().size());
        }
        
        // Group items by vimName/namespace
        Map<String, List<CNFChecklistItem>> itemsByNamespace = new HashMap<>();
        for (CNFChecklistItem item : request.getItems()) {
            String key = item.getVimName() + "/" + item.getNamespace();
            itemsByNamespace.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        
        List<CnfComparison> cnfResults = new ArrayList<>();
        
        // Process each namespace
        for (Map.Entry<String, List<CNFChecklistItem>> entry : itemsByNamespace.entrySet()) {
            String namespaceKey = entry.getKey();
            List<CNFChecklistItem> items = entry.getValue();
            
            String[] parts = namespaceKey.split("/");
            String vimName = parts[0];
            String namespace = parts[1];
            
            log.debug("Processing CNF results for {}: {} items", namespaceKey, items.size());
            
            CnfComparison cnfComp = CnfComparison.builder()
                    .vimName(vimName)
                    .namespace(namespace)
                    .items(new ArrayList<>())
                    .build();
            
            int matchCount = 0;
            int differenceCount = 0;
            int missingCount = 0;
            int ignoredCount = 0;
            int errorCount = 0;
            
            // Find the corresponding NamespaceComparison
            NamespaceComparison nsComparison = findNamespaceComparison(comparisons, vimName, namespace);
            
            if (nsComparison == null) {
                log.warn("No comparison found for {}", namespaceKey);
                continue;
            }
            
            // Process each checklist item
            for (CNFChecklistItem item : items) {
                // Use just object name as key (matching K8sDataCollector format)
                String objectKey = item.getObjectName();
                
                CnfComparison.CnfChecklistResult result = CnfComparison.CnfChecklistResult.builder()
                        .kind(item.getKind())
                        .objectName(item.getObjectName())
                        .fieldKey(item.getFieldKey())
                        .baselineValue(item.getManoValue())
                        .build();
                
                // Check if this field should be ignored
                if (validationConfig.shouldIgnore(item.getFieldKey())) {
                    result.setActualValue("(ignored)");
                    result.setStatus(CnfComparison.ValidationStatus.IGNORED);
                    result.setMessage("Field ignored by ignore rules");
                    ignoredCount++;
                    log.debug("Field '{}' marked as IGNORED by ignore rules", item.getFieldKey());
                    cnfComp.addItem(result);
                    continue;
                }
                
                ObjectComparison objComp = nsComparison.getObjectComparisons().get(objectKey);
                
                if (objComp == null) {
                    // Object not found in runtime
                    result.setActualValue(null);
                    result.setStatus(CnfComparison.ValidationStatus.MISSING_IN_RUNTIME);
                    result.setMessage("Object not found in runtime cluster");
                    missingCount++;
                    log.warn("Object '{}' not found in namespace comparison", item.getObjectName());
                } else {
                    log.info("Found object '{}' with {} field comparisons", 
                            item.getObjectName(), objComp.getItems().size());
                    
                    // Log first few keys for debugging
                    int maxKeysToLog = Math.min(20, objComp.getItems().size());
                    log.info("Sample keys from object (showing {} of {}):", maxKeysToLog, objComp.getItems().size());
                    for (int i = 0; i < maxKeysToLog; i++) {
                        KeyComparison kc = objComp.getItems().get(i);
                        log.info("  [{}] key='{}' left='{}' right='{}'", 
                                i, kc.getKey(), kc.getLeftValue(), kc.getRightValue());
                    }
                    
                    // Find the specific field comparison using matching strategy from request
                    String strategy = request.getMatchingStrategy() != null ? request.getMatchingStrategy() : "value";
                    log.info("CNF item validation: fieldKey='{}', manoValue='{}', matchingStrategy='{}'", 
                            item.getFieldKey(), item.getManoValue(), strategy);
                    KeyComparison fieldComp = findFieldComparison(objComp, item.getFieldKey(), item.getManoValue(), strategy);
                    
                    if (fieldComp == null) {
                        // Field not found
                        result.setActualValue(null);
                        result.setStatus(CnfComparison.ValidationStatus.MISSING_IN_RUNTIME);
                        result.setMessage("Field not found in runtime object");
                        missingCount++;
                    } else {
                        result.setActualValue(fieldComp.getRightValue());
                        
                        // Check if runtime value is null (field exists in structure but no value)
                        if (fieldComp.getRightValue() == null || "null".equals(fieldComp.getRightValue())) {
                            result.setStatus(CnfComparison.ValidationStatus.MISSING_IN_RUNTIME);
                            result.setMessage("Field exists in baseline but has no value in runtime");
                            missingCount++;
                            log.warn("Field '{}' has null value in runtime (expected: '{}')", 
                                    item.getFieldKey(), item.getManoValue());
                        } else {
                            // Store matched field key if different from requested (index remapping)
                            boolean indexRemapped = false;
                            if (!item.getFieldKey().equals(fieldComp.getKey()) && 
                                !item.getFieldKey().equals(fieldComp.getKey().replace("spec.", ""))) {
                                result.setMatchedFieldKey(fieldComp.getKey());
                                indexRemapped = true;
                                log.info("Index remapping: requested='{}' matched='{}' value='{}'", 
                                        item.getFieldKey(), fieldComp.getKey(), fieldComp.getRightValue());
                            }
                            
                            // For flexible matching (value/identity), if we found a match by value,
                            // the actual value MUST match the expected value (that's how we found it!)
                            // So override the status to MATCH regardless of what the comparison engine says
                            if (indexRemapped && "value".equals(strategy) && 
                                item.getManoValue() != null && item.getManoValue().equals(fieldComp.getRightValue())) {
                                result.setStatus(CnfComparison.ValidationStatus.MATCH);
                                result.setMessage(null); // Clear any diff message
                                matchCount++;
                                log.info("Flexible match confirmed: value='{}' (index remapped)", fieldComp.getRightValue());
                            } else {
                                // Map ComparisonStatus to ValidationStatus for exact match or no remapping
                                switch (fieldComp.getStatus()) {
                                    case MATCH:
                                    case BOTH_NULL:
                                        result.setStatus(CnfComparison.ValidationStatus.MATCH);
                                        matchCount++;
                                        break;
                                    case DIFFERENT:
                                        result.setStatus(CnfComparison.ValidationStatus.DIFFERENT);
                                        result.setMessage(String.format("Expected: %s, Actual: %s", 
                                                fieldComp.getLeftValue(), fieldComp.getRightValue()));
                                        differenceCount++;
                                        break;
                                    case ONLY_IN_RIGHT:
                                        result.setActualValue(fieldComp.getRightValue());
                                        result.setStatus(CnfComparison.ValidationStatus.MISSING_IN_RUNTIME);
                                        result.setMessage("Field exists in runtime but not in baseline");
                                        missingCount++;
                                        break;
                                    case ONLY_IN_LEFT:
                                        result.setStatus(CnfComparison.ValidationStatus.MISSING_IN_RUNTIME);
                                        result.setMessage("Field missing in runtime");
                                        missingCount++;
                                        break;
                                    default:
                                        result.setStatus(CnfComparison.ValidationStatus.ERROR);
                                        result.setMessage("Unknown comparison status");
                                        errorCount++;
                                }
                            }
                        }
                    }
                }
                
                cnfComp.addItem(result);
            }
            
            // Build summary
            CnfComparison.CnfSummary summary = CnfComparison.CnfSummary.builder()
                    .totalFields(items.size())
                    .matchCount(matchCount)
                    .differenceCount(differenceCount)
                    .missingCount(missingCount)
                    .ignoredCount(ignoredCount)
                    .errorCount(errorCount)
                    .build();
            
            cnfComp.setSummary(summary);
            cnfResults.add(cnfComp);
            
            log.info("CNF results for {}: {} total, {} match, {} diff, {} missing, {} ignored", 
                    namespaceKey, items.size(), matchCount, differenceCount, missingCount, ignoredCount);
        }
        
        return cnfResults;
    }
    
    /**
     * Find NamespaceComparison for given vim/namespace
     */
    private NamespaceComparison findNamespaceComparison(
            Map<String, NamespaceComparison> comparisons, 
            String vimName, 
            String namespace) {
        
        String comparisonKeyPrefix = vimName + "/" + namespace;
        
        for (Map.Entry<String, NamespaceComparison> entry : comparisons.entrySet()) {
            NamespaceComparison comp = entry.getValue();
            // Check if this comparison matches our vim/namespace
            // The labels are format: "vimName/namespace (Baseline)"
            if ((comp.getLeftNamespace() != null && comp.getLeftNamespace().startsWith(comparisonKeyPrefix)) ||
                (comp.getRightNamespace() != null && comp.getRightNamespace().startsWith(comparisonKeyPrefix))) {
                return comp;
            }
        }
        return null;
    }
    
    /**
     * Find KeyComparison for a specific field with matching strategy
     * 
     * @param objComp Object comparison result
     * @param fieldKey Field path (e.g., "spec.template.spec.containers[0].image")
     * @param expectedValue Expected value to match
     * @param strategy Matching strategy: "exact", "value", or "identity"
     * @return KeyComparison if found, null otherwise
     */
    private KeyComparison findFieldComparison(ObjectComparison objComp, String fieldKey, 
                                             String expectedValue, String strategy) {
        
        log.debug("Finding field comparison: fieldKey='{}', strategy='{}', expectedValue='{}'", 
                fieldKey, strategy, expectedValue);
        
        // Strategy 1: EXACT match - traditional exact field path
        if ("exact".equals(strategy)) {
            for (KeyComparison item : objComp.getItems()) {
                if (item.getKey().equals(fieldKey) || item.getKey().equals("spec." + fieldKey)) {
                    log.debug("Found exact match: '{}'", item.getKey());
                    return item;
                }
            }
            return null;
        }
        
        // Strategy 2: VALUE-based search - find by value in any list item (numeric index)
        // Example: containers[1].image with value "nginx:1.22" 
        // -> finds any container with image="nginx:1.22"
        // Nested example: containers[0].env[1].name -> finds env with matching name value
        if ("value".equals(strategy)) {
            if (!fieldKey.contains("[") || !fieldKey.contains("].")) {
                // Not a list field, fallback to exact match
                return findFieldComparison(objComp, fieldKey, expectedValue, "exact");
            }
            
            // Extract list components - use LAST "[" for nested arrays
            int lastBracketStart = fieldKey.lastIndexOf("[");
            int listStart = fieldKey.lastIndexOf(".", lastBracketStart);
            String prefix = listStart > 0 ? fieldKey.substring(0, listStart + 1) : "";
            String listName = fieldKey.substring(listStart + 1, lastBracketStart);
            int indexEnd = fieldKey.indexOf("]", lastBracketStart);
            String suffix = fieldKey.substring(indexEnd + 2); // Skip "].
            
            log.info("Value-based search: fieldKey='{}', listName='{}', prefix='{}', suffix='{}', value='{}'", 
                    fieldKey, listName, prefix, suffix, expectedValue);
            
            // Build search patterns - normalize spec. prefix handling
            // If prefix already starts with "spec.", don't add it again
            String searchPatternWithoutSpec = prefix.startsWith("spec.") ? 
                    prefix.substring(5) + listName + "[" : prefix + listName + "[";
            String searchPatternWithSpec = prefix.startsWith("spec.") ? 
                    prefix + listName + "[" : "spec." + prefix + listName + "[";
            
            log.debug("Search patterns: without='{}', with='{}'", searchPatternWithoutSpec, searchPatternWithSpec);
            
            // Log all available keys for debugging
            log.info("Available keys in object ({} total):", objComp.getItems().size());
            for (KeyComparison item : objComp.getItems()) {
                if (item.getKey().contains(listName + "[")) {
                    log.info("  - key='{}' left='{}' right='{}'", item.getKey(), item.getLeftValue(), item.getRightValue());
                }
            }
            
            // Search for any item in the list with matching value
            for (KeyComparison item : objComp.getItems()) {
                String key = item.getKey();
                
                // Check if key matches pattern and has correct suffix
                boolean patternMatches = (key.startsWith(searchPatternWithoutSpec) || 
                                         key.startsWith(searchPatternWithSpec)) && 
                                        key.endsWith("." + suffix);
                
                if (patternMatches) {
                    log.debug("Checking key='{}' with left='{}' right='{}' against expected='{}'",
                            key, item.getLeftValue(), item.getRightValue(), expectedValue);
                    
                    // CRITICAL: For value search, we match by ACTUAL (right) value, not baseline (left)
                    // We're searching: "find the env item whose .name field has value DB_HOST in runtime"
                    if (expectedValue != null && expectedValue.equals(item.getRightValue())) {
                        log.info("Found value-based match in {}: requested='{}' matched='{}' value='{}'", 
                                listName, fieldKey, key, item.getRightValue());
                        // Return the item as-is to preserve actual matched index
                        return item;
                    }
                }
            }
            
            log.info("No value-based match found for field '{}' with value '{}'. Checked patterns: '{}' or '{}'", 
                    suffix, expectedValue, searchPatternWithoutSpec, searchPatternWithSpec);
            return null;
        }
        
        // Strategy 3: IDENTITY-based search - semantic/name-based (V2 format)
        // Example: containers[nginx].image
        // -> matches exactly containers[nginx].image from V2 semantic comparison
        if ("identity".equals(strategy)) {
            log.info("Identity-based search for fieldKey='{}', expectedValue='{}'", fieldKey, expectedValue);
            
            // Log all available keys for debugging
            log.info("Available keys in object ({} total):", objComp.getItems().size());
            for (KeyComparison item : objComp.getItems()) {
                log.info("  - key='{}' left='{}' right='{}'", item.getKey(), item.getLeftValue(), item.getRightValue());
            }
            
            // Try exact match first (V2 semantic keys use identity)
            for (KeyComparison item : objComp.getItems()) {
                if (item.getKey().equals(fieldKey) || item.getKey().equals("spec." + fieldKey)) {
                    log.info("Found identity-based exact match: '{}'", item.getKey());
                    return item;
                }
            }
            
            // Try partial match - match suffix with identity brackets
            // Example: for "containers[app].env[DB_HOST].name", we'll match any key ending with
            // same pattern of identity brackets and suffix
            for (KeyComparison item : objComp.getItems()) {
                String key = item.getKey();
                
                // Remove spec. prefix from both for comparison
                String normalizedKey = key.startsWith("spec.") ? key.substring(5) : key;
                String normalizedFieldKey = fieldKey.startsWith("spec.") ? fieldKey.substring(5) : fieldKey;
                
                // Check if normalized keys match
                if (normalizedKey.equals(normalizedFieldKey)) {
                    log.info("Found identity-based normalized match: requested='{}' matched='{}'", 
                            fieldKey, key);
                    return item;
                }
                
                // Also try suffix matching - useful for nested identities
                // Extract last identity bracket + suffix: e.g., "env[DB_HOST].name"
                int lastBracket = normalizedFieldKey.lastIndexOf("[");
                if (lastBracket > 0) {
                    int dotBeforeBracket = normalizedFieldKey.lastIndexOf(".", lastBracket);
                    String searchSuffix = dotBeforeBracket > 0 ? 
                            normalizedFieldKey.substring(dotBeforeBracket + 1) : normalizedFieldKey;
                    
                    if (normalizedKey.endsWith(searchSuffix)) {
                        log.info("Found identity-based suffix match: requested='{}' matched='{}' suffix='{}'", 
                                fieldKey, key, searchSuffix);
                        return item;
                    }
                }
            }
            
            log.info("No identity-based match found for fieldKey='{}'", fieldKey);
            return null;
        }
        
        // Unknown strategy, fallback to exact
        log.warn("Unknown matching strategy '{}', using exact match", strategy);
        return findFieldComparison(objComp, fieldKey, expectedValue, "exact");
    }
}
