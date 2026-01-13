package com.nfv.validator.model.api;

/**
 * Status of a validation job
 */
public enum JobStatus {
    PENDING,      // Job is queued but not started
    PROCESSING,   // Job is currently running
    COMPLETED,    // Job finished successfully
    FAILED,       // Job finished with errors
    CANCELLED     // Job was cancelled
}
