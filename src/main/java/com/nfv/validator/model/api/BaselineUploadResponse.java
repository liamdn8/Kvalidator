package com.nfv.validator.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for baseline file uploads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineUploadResponse {

    /**
     * Absolute path of the stored baseline file on the server.
     */
    @JsonProperty("path")
    private String path;

    /**
     * Original filename provided by the user.
     */
    @JsonProperty("filename")
    private String filename;

    /**
     * Size of the stored file in bytes.
     */
    @JsonProperty("size")
    private long size;
}
