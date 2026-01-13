package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response model for validation job status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationJobResponse {
    
    /**
     * Unique job identifier
     */
    @JsonProperty("jobId")
    private String jobId;
    
    /**
     * Current status of the job
     */
    @JsonProperty("status")
    private JobStatus status;
    
    /**
     * Progress information (only for PROCESSING status)
     */
    @JsonProperty("progress")
    private JobProgress progress;
    
    /**
     * Optional message (e.g., error message for FAILED status)
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * Job submission timestamp
     */
    @JsonProperty("submittedAt")
    private Instant submittedAt;
    
    /**
     * Job start timestamp
     */
    @JsonProperty("startedAt")
    private Instant startedAt;
    
    /**
     * Job completion timestamp
     */
    @JsonProperty("completedAt")
    private Instant completedAt;
    
    /**
     * Path to result directory (when completed)
     */
    @JsonProperty("reportPath")
    private String reportPath;
    
    /**
     * Download URL for Excel report
     */
    @JsonProperty("downloadUrl")
    private String downloadUrl;
    
    /**
     * URL to get JSON results
     */
    @JsonProperty("jsonUrl")
    private String jsonUrl;
    
    /**
     * Total differences found (when completed)
     */
    @JsonProperty("differencesFound")
    private Integer differencesFound;
    
    /**
     * Total objects compared (when completed)
     */
    @JsonProperty("objectsCompared")
    private Integer objectsCompared;
}
