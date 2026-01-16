package com.nfv.validator.model.batch;

import com.nfv.validator.model.FlatNamespaceModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a single validation request in a batch
 * Can be namespace comparison or baseline validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequest {
    
    /**
     * Unique name/identifier for this request
     */
    private String name;
    
    /**
     * Type of validation: "namespace-comparison" or "baseline-comparison"
     * Auto-detected if not specified:
     * - If baseline is set -> "baseline-comparison"
     * - If baseline is null -> "namespace-comparison"
     */
    private String type;
    
    /**
     * List of namespaces to compare (format: "cluster/namespace" or just "namespace")
     * For namespace-comparison: at least 2 namespaces required
     * For baseline-comparison: at least 1 namespace required
     */
    private List<String> namespaces;
    
    /**
     * Path to baseline YAML file or directory (for baseline-comparison type)
     */
    private String baseline;
    
    /**     * Flattened baseline model for direct comparison (alternative to baseline file)
     * Used when converting CNF checklists to avoid YAML file creation
     */
    private Map<String, FlatNamespaceModel> flattenedBaseline;
    
    /**     * Default cluster name if not specified in namespace string
     */
    private String defaultCluster;
    
    /**
     * Resource kinds to compare (e.g., ["Deployment", "Service"])
     * If null or empty, compare all resource types
     */
    private List<String> kinds;
    
    /**
     * Path to custom validation config file
     * If null, uses default validation-config.yaml
     */
    private String configFile;
    
    /**
     * Output file path for this specific request (Excel file)
     * If null, no file output for this request
     */
    private String output;
    
    /**
     * Whether to show verbose output for this request
     */
    private boolean verbose;
    
    /**
     * Validate the request parameters
     */
    public void validate() throws IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Request name is required");
        }
        
        // Auto-detect type if not specified
        // Auto-detect type if not specified
        if (type == null || type.trim().isEmpty()) {
            if (baseline != null && !baseline.trim().isEmpty()) {
                type = "baseline-comparison";
            } else {
                type = "namespace-comparison";
            }
        }
        
        // Auto-generate output filename from name if not specified
        if (output == null || output.trim().isEmpty()) {
            // Normalize name to lowercase, replace spaces/special chars with hyphens
            String normalizedName = name.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-+|-+$", ""); // Remove leading/trailing hyphens
            output = normalizedName + ".xlsx";
        }
        
        if (!"namespace-comparison".equals(type) && !"baseline-comparison".equals(type)) {
            throw new IllegalArgumentException(
                "Invalid request type: " + type + ". Must be 'namespace-comparison' or 'baseline-comparison'"
            );
        }
        
        if (namespaces == null || namespaces.isEmpty()) {
            throw new IllegalArgumentException("At least one namespace is required for: " + name);
        }
        
        if ("namespace-comparison".equals(type) && namespaces.size() < 2) {
            throw new IllegalArgumentException(
                "Namespace comparison requires at least 2 namespaces for: " + name
            );
        }
        
        if ("baseline-comparison".equals(type) && (baseline == null || baseline.trim().isEmpty())) {
            throw new IllegalArgumentException(
                "Baseline path is required for baseline-comparison type in: " + name
            );
        }
    }
}
