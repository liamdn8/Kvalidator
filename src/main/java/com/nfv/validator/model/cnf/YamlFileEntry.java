package com.nfv.validator.model.cnf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entry for a single YAML file in batch conversion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YamlFileEntry {
    
    /**
     * File name (for reference)
     */
    private String fileName;
    
    /**
     * YAML content
     */
    private String yamlContent;
    
    /**
     * Optional description
     */
    private String description;
}
