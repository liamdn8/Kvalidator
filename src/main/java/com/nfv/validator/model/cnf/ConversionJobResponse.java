package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for YAML to CNF conversion job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionJobResponse {
    
    /**
     * Job ID
     */
    private String jobId;
    
    /**
     * Job status: PENDING, PROCESSING, COMPLETED, FAILED
     */
    private String status;
    
    /**
     * Target namespace for this conversion job
     */
    private String targetNamespace;
    
    /**
     * Number of YAML files
     */
    private int fileCount;
    
    /**
     * Number of namespaces (if filtered)
     */
    private Integer namespaceCount;
    
    /**
     * Namespaces being processed
     */
    private List<String> namespaces;
    
    /**
     * Flatten mode
     */
    private String flattenMode;
    
    /**
     * Total items generated (when completed)
     */
    private Integer totalItems;
    
    /**
     * Excel file path (when completed)
     */
    private String excelFilePath;
    
    /**
     * Progress percentage (0-100)
     */
    private Integer progress;
    
    /**
     * Error message (if failed)
     */
    private String errorMessage;
    
    /**
     * Job description
     */
    private String description;
    
    /**
     * Submission time
     */
    private LocalDateTime submittedAt;
    
    /**
     * Completion time
     */
    private LocalDateTime completedAt;
}
