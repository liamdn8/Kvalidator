package com.nfv.validator.config;

import lombok.Data;

/**
 * Feature flags for validation engine
 * Controls which version of comparison engine to use
 */
@Data
public class FeatureFlags {
    
    /**
     * Use V2 semantic comparison engine
     * 
     * V2 Engine benefits:
     * - Order-independent comparison for list items (containers, volumes, env vars)
     * - Identity-based matching (matches by name, not by index)
     * - More accurate semantic comparison
     * - Eliminates false positives from ordering changes
     * 
     * Default: true (V2 enabled)
     */
    private boolean useSemanticComparison = true;
    
    /**
     * Enable verbose logging for V2 comparisons
     * Useful for debugging semantic matching logic
     */
    private boolean verboseSemanticLogging = false;
    
    /**
     * Convert V2 results back to flat format for API compatibility
     * Should be true unless you want to change API response format
     */
    private boolean convertToFlatFormat = true;
    
    /**
     * Enable experimental features
     */
    private boolean enableExperimentalFeatures = false;
    
    /**
     * Singleton instance
     */
    private static FeatureFlags instance;
    
    public static FeatureFlags getInstance() {
        if (instance == null) {
            instance = new FeatureFlags();
            
            // Load from environment variables if available
            String useV2 = System.getenv("USE_SEMANTIC_COMPARISON");
            if (useV2 != null) {
                instance.useSemanticComparison = Boolean.parseBoolean(useV2);
            }
            
            String verbose = System.getenv("VERBOSE_SEMANTIC_LOGGING");
            if (verbose != null) {
                instance.verboseSemanticLogging = Boolean.parseBoolean(verbose);
            }
        }
        return instance;
    }
    
    /**
     * Reset to defaults (useful for testing)
     */
    public static void reset() {
        instance = null;
    }
}
