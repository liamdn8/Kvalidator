# Config Flatten Feature - Business Analysis & Implementation Plan

## Executive Summary

Tài liệu này mô tả thiết kế và kế hoạch phát triển cho tính năng **Configuration Flattening** - một phần mở rộng quan trọng của nhóm tính năng YAML to CNF nhằm hỗ trợ flatten và chuẩn hóa các file cấu hình từ nhiều định dạng khác nhau sang CNF Checklist template Excel.

**Mục tiêu chính:**
- Hỗ trợ validate file cấu hình application sâu hơn (không chỉ Kubernetes resources)
- Tích hợp với CNF Checklist workflow hiện có
- Mở rộng khả năng quản lý cấu hình cross-platform (Kubernetes ConfigMap + Linux Service configs)

---

## 1. Business Context

### 1.1. Problem Statement

Hiện tại, KValidator có thể:
- ✅ Validate Kubernetes resources (Deployment, Service, ConfigMap, etc.)
- ✅ Convert YAML to CNF Checklist
- ✅ Execute CNF Checklist validation

Tuy nhiên, trong thực tế triển khai NFV/CNF, các application configuration files thường tồn tại ở nhiều định dạng khác nhau:

| Loại File | Format | Vị trí | Ví dụ |
|-----------|--------|--------|-------|
| Kubernetes ConfigMap | YAML/JSON | K8s cluster | nginx-config, app-settings |
| Application Config | YAML | ConfigMap/File | application.yaml, config.yml |
| Java Properties | .properties | ConfigMap/File | database.properties |
| Linux Service Config | .conf | Worker nodes | nginx.conf, haproxy.cfg |
| Structured Data | JSON | ConfigMap/File | settings.json |

**Vấn đề:**
1. Không có cách tiếp cận thống nhất để validate các file config này
2. Khó theo dõi các thay đổi cấu hình khi deploy lên môi trường khác nhau
3. Không có template chuẩn để kiểm tra tính đầy đủ của cấu hình

### 1.2. Business Value

**Lợi ích trực tiếp:**
- ⚡ **Tăng coverage validation**: Mở rộng từ K8s resources sang application configs
- 🔍 **Deep configuration validation**: Validate từng field/parameter trong config
- 📊 **Standardization**: Chuẩn hóa cách quản lý cấu hình qua Excel template
- 🔄 **Integration**: Tích hợp mượt mà với CNF Checklist workflow hiện có

**Use cases thực tế:**
- DevOps team cần validate nginx config trước khi apply vào cluster
- QA team cần check database connection properties giữa các environments
- NFV team cần đảm bảo haproxy config đồng nhất trên các worker nodes

---

## 2. Feature Requirements

### 2.1. Functional Requirements

#### FR-1: Multi-Format Configuration Parsing

**Mô tả:** Hệ thống phải hỗ trợ parse và flatten các định dạng file sau:

| Format | Extension | Priority | Parsing Strategy |
|--------|-----------|----------|------------------|
| YAML | .yaml, .yml | P0 | Jackson YAML parser |
| JSON | .json | P0 | Jackson JSON parser |
| Properties | .properties | P1 | Java Properties class |
| CONF | .conf, .cfg | P1 | Custom key-value parser |
| TOML | .toml | P2 | TOML parser library |
| XML | .xml | P2 | Jackson XML parser |

**Acceptance Criteria:**
- ✅ Parse nested structures thành flat key-value pairs
- ✅ Preserve type information (string, number, boolean, array)
- ✅ Handle array indexing: `servers[0].host`
- ✅ Support dot notation: `database.connection.url`

**Example:**
```yaml
# Input: application.yaml
server:
  port: 8080
  ssl:
    enabled: true
    keystore: /path/to/keystore.jks

database:
  connections:
    - host: db1.example.com
      port: 5432
    - host: db2.example.com
      port: 5432
```

```
# Output (flattened):
server.port = 8080
server.ssl.enabled = true
server.ssl.keystore = /path/to/keystore.jks
database.connections[0].host = db1.example.com
database.connections[0].port = 5432
database.connections[1].host = db2.example.com
database.connections[1].port = 5432
```

---

#### FR-2: Deployment Target Association

**Mô tả:** Mỗi config file phải được mapping với deployment target cụ thể:

**Deployment Target Types:**

##### Type A: Kubernetes ConfigMap/Secret
```json
{
  "targetType": "configmap",
  "namespace": "production",
  "objectName": "nginx-config",
  "mountPath": "/etc/nginx/nginx.conf"  // optional
}
```

##### Type B: Linux Service (Worker Nodes)
```json
{
  "targetType": "worker-node",
  "nodeSelector": "role=lb",
  "serviceName": "haproxy",
  "configPath": "/etc/haproxy/haproxy.cfg"
}
```

**Use Case Scenarios:**

| Scenario | Target Type | Description |
|----------|-------------|-------------|
| K8s App Config | ConfigMap | application.yaml được mount vào Pod |
| Nginx Config | ConfigMap hoặc Worker Node | nginx.conf có thể ở ConfigMap hoặc trực tiếp trên node |
| Database Properties | ConfigMap | database.properties trong ConfigMap |
| HAProxy Config | Worker Node | haproxy.cfg trên worker nodes |

**Acceptance Criteria:**
- ✅ Support cả 2 target types
- ✅ Validate target metadata (namespace exists, node selector valid)
- ✅ Multiple targets cho cùng 1 config file (multi-environment)

---

#### FR-3: Excel CNF Checklist Generation

**Mô tả:** Generate Excel file theo đúng format CNF Checklist template hiện có.

**Extended Column Schema:**

| Column | Description | Example | Mandatory |
|--------|-------------|---------|-----------|
| VIM Name | Cluster identifier | kind-kind-infra-test | ✅ |
| Target Type | configmap / worker-node | configmap | ✅ |
| Namespace | K8s namespace (if configmap) | production | Conditional |
| Object Name | ConfigMap/Secret name hoặc Service name | nginx-config | ✅ |
| Node Selector | Node selector (if worker-node) | role=lb | Conditional |
| Config Path | Path on worker node | /etc/nginx/nginx.conf | Conditional |
| Field Key | Flattened config key | server.ssl.enabled | ✅ |
| Expected Value | Configuration value | true | ✅ |
| Value Type | string/number/boolean/array | boolean | Optional |

**Sheet Structure:**
1. **Sheet 1: "CNF Checklist"** - Main data
2. **Sheet 2: "Metadata"** (NEW) - File metadata, parsing info
3. **Sheet 3: "Validation Rules"** (Optional) - Custom validation rules

**Acceptance Criteria:**
- ✅ Compatible với CNF Checklist upload API hiện có
- ✅ Auto-sizing columns
- ✅ Header styling (bold, colored background)
- ✅ Data validation cho các columns (dropdown cho Target Type)

---

#### FR-4: Smart Field Selection

**Mô tả:** Cho phép user chọn fields nào cần extract (không phải tất cả)

**Selection Modes:**

##### Mode 1: Full Extraction (Default)
- Extract tất cả fields trong config file
- Suitable cho comprehensive validation

##### Mode 2: Important Fields Only
- User chọn list các field keys quan trọng
- Filter chỉ extract các fields này
- Example: `["server.port", "database.*.host", "ssl.enabled"]`

##### Mode 3: Pattern-Based
- Support wildcard patterns
- Example:
  - `server.*` - tất cả fields trong `server` section
  - `*.port` - tất cả fields có tên `port`
  - `database.connections[*].host` - host của tất cả database connections

**Acceptance Criteria:**
- ✅ Default mode extract tất cả
- ✅ Support custom field list
- ✅ Support wildcard patterns
- ✅ Validate field patterns (báo lỗi nếu pattern không match)

---

#### FR-5: Batch Processing

**Mô tả:** Process multiple config files cùng lúc, merge vào 1 Excel file.

**Input:**
```json
{
  "vimName": "vim-hanoi",
  "files": [
    {
      "fileName": "nginx.conf",
      "fileContent": "...",
      "format": "conf",
      "targets": [
        {
          "targetType": "worker-node",
          "nodeSelector": "role=nginx",
          "serviceName": "nginx",
          "configPath": "/etc/nginx/nginx.conf"
        }
      ]
    },
    {
      "fileName": "application.yaml",
      "fileContent": "...",
      "format": "yaml",
      "targets": [
        {
          "targetType": "configmap",
          "namespace": "production",
          "objectName": "app-config"
        }
      ]
    }
  ],
  "fieldSelectionMode": "important",
  "importantFields": ["server.port", "database.*.host"]
}
```

**Output:**
- Single Excel file với tất cả config items
- Group by file name (separate sections hoặc color coding)

**Acceptance Criteria:**
- ✅ Process up to 50 files trong 1 batch
- ✅ Deduplicate identical entries
- ✅ Preserve file source information
- ✅ Generate summary sheet (số lượng files, items, etc.)

---

### 2.2. Non-Functional Requirements

#### NFR-1: Performance
- Parse và flatten 1 config file (< 10KB) trong **< 100ms**
- Generate Excel cho 1000 items trong **< 2 seconds**
- Batch process 50 files trong **< 30 seconds**

#### NFR-2: Compatibility
- Backward compatible với CNF Checklist API hiện có
- Excel output có thể upload vào CNF Checklist validation endpoint
- Support UTF-8 encoding cho tất cả formats

#### NFR-3: Error Handling
- Graceful handling khi parse fails (skip hoặc log error)
- Clear error messages với line number nếu parse error
- Validation cho file format trước khi parse

#### NFR-4: Scalability
- Support files up to 5MB
- Memory-efficient parsing (streaming nếu cần)
- Configurable limits (max items per file, max files per batch)

---

## 3. Technical Design

### 3.1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                              │
│                                                                        │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  ConfigFlattenPage.tsx                                      │      │
│  │  - Upload multiple config files                             │      │
│  │  - Select file formats                                      │      │
│  │  - Configure deployment targets                             │      │
│  │  - Choose field selection mode                              │      │
│  └────────────────────────────────────────────────────────────┘      │
│                              │ HTTP POST                              │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Backend REST API (Quarkus)                         │
│                                                                        │
│  POST /kvalidator/api/config-flatten/convert                          │
│  POST /kvalidator/api/config-flatten/preview                          │
│  POST /kvalidator/api/config-flatten/validate-target                  │
│                                                                        │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  ConfigFlattenResource.java                                 │      │
│  │  - Handle file uploads                                      │      │
│  │  - Validate requests                                        │      │
│  │  - Return Excel download                                    │      │
│  └────────────────────────────────────────────────────────────┘      │
│                              │                                        │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      Service Layer                                    │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────┐        │
│  │  ConfigFlattenService.java                               │        │
│  │  - Orchestrate parsing và flatten process                │        │
│  │  - Merge results từ multiple files                       │        │
│  │  - Generate CNF checklist items                          │        │
│  └────────────┬────────────────────────────────────┬────────┘        │
│               │                                     │                 │
│               ▼                                     ▼                 │
│  ┌──────────────────────────┐      ┌─────────────────────────┐      │
│  │  ConfigParserFactory     │      │  DeploymentTarget       │      │
│  │  - Create parser based   │      │  Validator              │      │
│  │    on file format        │      │  - Validate K8s targets │      │
│  └──────────┬───────────────┘      │  - Validate node sel.   │      │
│             │                       └─────────────────────────┘      │
│             │                                                         │
│             ▼                                                         │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │         Parser Implementations                             │      │
│  │                                                             │      │
│  │  YamlConfigParser.java    - Parse YAML files              │      │
│  │  JsonConfigParser.java    - Parse JSON files              │      │
│  │  PropertiesConfigParser.java - Parse .properties          │      │
│  │  ConfConfigParser.java    - Parse .conf/.cfg              │      │
│  │  TomlConfigParser.java    - Parse TOML files              │      │
│  │  XmlConfigParser.java     - Parse XML files               │      │
│  │                                                             │      │
│  │  All implement: ConfigParser interface                     │      │
│  └───────────────────────────────────────────────────────────┘      │
│                              │                                        │
│                              ▼                                        │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │  ConfigFlattenExcelGenerator.java                        │       │
│  │  - Generate Excel với extended schema                    │       │
│  │  - Reuse CNFChecklistFileParser logic                    │       │
│  │  - Add metadata sheet                                    │       │
│  └──────────────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────────────┘
```

---

### 3.2. Data Models

#### 3.2.1. ConfigFlattenRequest

```java
package com.nfv.validator.model.config;

import lombok.Data;
import java.util.List;

@Data
public class ConfigFlattenRequest {
    
    /**
     * VIM/Cluster name for all config files
     */
    private String vimName;
    
    /**
     * List of config files to process
     */
    private List<ConfigFileEntry> files;
    
    /**
     * Field selection mode: "full", "important", "pattern"
     */
    private String fieldSelectionMode = "full";
    
    /**
     * Important fields to extract (used when mode = "important" or "pattern")
     */
    private List<String> importantFields;
    
    /**
     * Whether to deduplicate identical entries
     */
    private boolean deduplicateEntries = true;
    
    /**
     * Whether to include metadata sheet in Excel
     */
    private boolean includeMetadata = true;
}
```

#### 3.2.2. ConfigFileEntry

```java
package com.nfv.validator.model.config;

import lombok.Data;
import java.util.List;

@Data
public class ConfigFileEntry {
    
    /**
     * File name (e.g., "nginx.conf", "application.yaml")
     */
    private String fileName;
    
    /**
     * File content (base64 encoded or plain text)
     */
    private String fileContent;
    
    /**
     * File format: yaml, json, properties, conf, toml, xml
     */
    private String format;
    
    /**
     * Deployment targets for this config file
     * Can have multiple targets (e.g., different namespaces)
     */
    private List<DeploymentTarget> targets;
}
```

#### 3.2.3. DeploymentTarget

```java
package com.nfv.validator.model.config;

import lombok.Data;

@Data
public class DeploymentTarget {
    
    /**
     * Target type: "configmap", "secret", "worker-node"
     */
    private String targetType;
    
    // --- Fields for ConfigMap/Secret target ---
    
    /**
     * Kubernetes namespace (required if targetType = configmap/secret)
     */
    private String namespace;
    
    /**
     * ConfigMap or Secret name (required if targetType = configmap/secret)
     */
    private String objectName;
    
    /**
     * Mount path in Pod (optional)
     */
    private String mountPath;
    
    // --- Fields for Worker Node target ---
    
    /**
     * Node selector (required if targetType = worker-node)
     */
    private String nodeSelector;
    
    /**
     * Service name on worker node (required if targetType = worker-node)
     */
    private String serviceName;
    
    /**
     * Config file path on worker node (required if targetType = worker-node)
     */
    private String configPath;
}
```

#### 3.2.4. FlattenedConfigItem

```java
package com.nfv.validator.model.config;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class FlattenedConfigItem {
    
    /**
     * Source file name
     */
    private String sourceFile;
    
    /**
     * Flattened key path (e.g., "server.port", "database.connections[0].host")
     */
    private String fieldKey;
    
    /**
     * Configuration value
     */
    private String value;
    
    /**
     * Value type: string, number, boolean, array, object
     */
    private String valueType;
    
    /**
     * Deployment target information
     */
    private DeploymentTarget target;
    
    /**
     * VIM name
     */
    private String vimName;
}
```

#### 3.2.5. ExtendedCNFChecklistItem

```java
package com.nfv.validator.model.cnf;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Extended CNF Checklist Item với thông tin deployment target
 * Backward compatible với CNFChecklistItem
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedCNFChecklistItem extends CNFChecklistItem {
    
    /**
     * Target type: "configmap", "secret", "worker-node"
     * Null for standard Kubernetes resources
     */
    private String targetType;
    
    /**
     * Node selector (for worker-node targets)
     */
    private String nodeSelector;
    
    /**
     * Config path on worker node
     */
    private String configPath;
    
    /**
     * Value type for better validation
     */
    private String valueType;
    
    /**
     * Source file name
     */
    private String sourceFile;
}
```

---

### 3.3. Core Interfaces

#### 3.3.1. ConfigParser Interface

```java
package com.nfv.validator.service.parser;

import com.nfv.validator.model.config.FlattenedConfigItem;
import java.util.List;

/**
 * Interface for parsing different config file formats
 */
public interface ConfigParser {
    
    /**
     * Parse config file content and flatten to key-value pairs
     * 
     * @param fileName File name (for reference)
     * @param fileContent File content as string
     * @param importantFields Optional list of important fields to extract (null = extract all)
     * @return List of flattened config items
     * @throws ConfigParseException if parsing fails
     */
    List<FlattenedConfigItem> parse(String fileName, String fileContent, List<String> importantFields) 
        throws ConfigParseException;
    
    /**
     * Check if this parser supports the given file format
     * 
     * @param format File format (yaml, json, properties, etc.)
     * @return true if supported
     */
    boolean supports(String format);
    
    /**
     * Validate config content before parsing
     * 
     * @param fileContent File content
     * @return true if valid
     */
    boolean validate(String fileContent);
}
```

---

### 3.4. Parser Implementations

#### 3.4.1. YamlConfigParser

```java
package com.nfv.validator.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nfv.validator.model.config.FlattenedConfigItem;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.util.*;

/**
 * Parser for YAML config files
 * Reuse logic từ YamlToCNFChecklistConverter
 */
@Slf4j
@ApplicationScoped
public class YamlConfigParser implements ConfigParser {
    
    private final ObjectMapper yamlMapper;
    
    public YamlConfigParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    @Override
    public List<FlattenedConfigItem> parse(String fileName, String fileContent, 
                                          List<String> importantFields) throws ConfigParseException {
        log.info("Parsing YAML file: {}", fileName);
        
        try {
            Map<String, Object> yamlData = yamlMapper.readValue(fileContent, Map.class);
            List<FlattenedConfigItem> items = new ArrayList<>();
            
            // Flatten nested structure
            flattenMap("", yamlData, items, fileName, importantFields);
            
            log.info("Parsed {} items from YAML file: {}", items.size(), fileName);
            return items;
            
        } catch (Exception e) {
            log.error("Failed to parse YAML file: {}", fileName, e);
            throw new ConfigParseException("Invalid YAML format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Recursively flatten nested Map structure
     */
    private void flattenMap(String prefix, Map<String, Object> map, 
                           List<FlattenedConfigItem> items, String fileName,
                           List<String> importantFields) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                // Recursive for nested maps
                flattenMap(key, (Map<String, Object>) value, items, fileName, importantFields);
                
            } else if (value instanceof List) {
                // Handle arrays with indexing
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String arrayKey = key + "[" + i + "]";
                    
                    if (item instanceof Map) {
                        flattenMap(arrayKey, (Map<String, Object>) item, items, fileName, importantFields);
                    } else {
                        addItem(arrayKey, item, items, fileName, importantFields);
                    }
                }
                
            } else {
                // Primitive value
                addItem(key, value, items, fileName, importantFields);
            }
        }
    }
    
    /**
     * Add flattened item if matches important fields filter
     */
    private void addItem(String key, Object value, List<FlattenedConfigItem> items,
                        String fileName, List<String> importantFields) {
        // Filter by important fields if specified
        if (importantFields != null && !matchesPattern(key, importantFields)) {
            return;
        }
        
        FlattenedConfigItem item = FlattenedConfigItem.builder()
            .sourceFile(fileName)
            .fieldKey(key)
            .value(value != null ? value.toString() : "")
            .valueType(detectType(value))
            .build();
            
        items.add(item);
    }
    
    /**
     * Check if key matches any pattern in important fields
     */
    private boolean matchesPattern(String key, List<String> patterns) {
        for (String pattern : patterns) {
            if (matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Match key against pattern (support wildcard *)
     * Examples:
     * - "server.*" matches "server.port", "server.host"
     * - "*.port" matches "server.port", "database.port"
     * - "database.connections[*].host" matches "database.connections[0].host", "database.connections[1].host"
     */
    private boolean matchPattern(String key, String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("[*]", "\\[\\d+\\]")
            .replace("*", ".*");
        return key.matches(regex);
    }
    
    /**
     * Detect value type
     */
    private String detectType(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Long) return "number";
        if (value instanceof Double || value instanceof Float) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List) return "array";
        if (value instanceof Map) return "object";
        return "string";
    }
    
    @Override
    public boolean supports(String format) {
        return "yaml".equalsIgnoreCase(format) || "yml".equalsIgnoreCase(format);
    }
    
    @Override
    public boolean validate(String fileContent) {
        try {
            yamlMapper.readValue(fileContent, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### 3.4.2. JsonConfigParser

Similar to YamlConfigParser, chỉ khác ObjectMapper

```java
package com.nfv.validator.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
// ... (similar structure to YamlConfigParser)

@Slf4j
@ApplicationScoped
public class JsonConfigParser implements ConfigParser {
    
    private final ObjectMapper jsonMapper;
    
    public JsonConfigParser() {
        this.jsonMapper = new ObjectMapper();
    }
    
    // Reuse flattenMap logic từ YamlConfigParser
    
    @Override
    public boolean supports(String format) {
        return "json".equalsIgnoreCase(format);
    }
}
```

#### 3.4.3. PropertiesConfigParser

```java
package com.nfv.validator.service.parser;

import com.nfv.validator.model.config.FlattenedConfigItem;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.StringReader;
import java.util.*;

/**
 * Parser for Java .properties files
 * Format: key=value (already flat, no nesting)
 */
@Slf4j
@ApplicationScoped
public class PropertiesConfigParser implements ConfigParser {
    
    @Override
    public List<FlattenedConfigItem> parse(String fileName, String fileContent, 
                                          List<String> importantFields) throws ConfigParseException {
        log.info("Parsing properties file: {}", fileName);
        
        try {
            Properties props = new Properties();
            props.load(new StringReader(fileContent));
            
            List<FlattenedConfigItem> items = new ArrayList<>();
            
            for (String key : props.stringPropertyNames()) {
                // Filter by important fields if specified
                if (importantFields != null && !importantFields.contains(key)) {
                    continue;
                }
                
                String value = props.getProperty(key);
                
                FlattenedConfigItem item = FlattenedConfigItem.builder()
                    .sourceFile(fileName)
                    .fieldKey(key)
                    .value(value)
                    .valueType("string")  // Properties always string
                    .build();
                    
                items.add(item);
            }
            
            log.info("Parsed {} items from properties file: {}", items.size(), fileName);
            return items;
            
        } catch (Exception e) {
            log.error("Failed to parse properties file: {}", fileName, e);
            throw new ConfigParseException("Invalid properties format: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean supports(String format) {
        return "properties".equalsIgnoreCase(format) || "props".equalsIgnoreCase(format);
    }
    
    @Override
    public boolean validate(String fileContent) {
        try {
            Properties props = new Properties();
            props.load(new StringReader(fileContent));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### 3.4.4. ConfConfigParser

```java
package com.nfv.validator.service.parser;

import com.nfv.validator.model.config.FlattenedConfigItem;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for .conf/.cfg files (nginx, haproxy style)
 * Supports key-value và block structures
 * 
 * Example nginx.conf:
 * worker_processes 4;
 * events {
 *     worker_connections 1024;
 * }
 */
@Slf4j
@ApplicationScoped
public class ConfConfigParser implements ConfigParser {
    
    // Pattern for key-value: "key value;" or "key value"
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^\\s*([\\w_]+)\\s+([^;{]+);?\\s*$");
    
    // Pattern for block start: "blockname {"
    private static final Pattern BLOCK_START_PATTERN = Pattern.compile("^\\s*([\\w_]+)\\s*\\{\\s*$");
    
    @Override
    public List<FlattenedConfigItem> parse(String fileName, String fileContent, 
                                          List<String> importantFields) throws ConfigParseException {
        log.info("Parsing conf file: {}", fileName);
        
        try {
            List<FlattenedConfigItem> items = new ArrayList<>();
            String[] lines = fileContent.split("\n");
            
            Stack<String> blockStack = new Stack<>();
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Check for block end
                if (line.equals("}")) {
                    if (!blockStack.isEmpty()) {
                        blockStack.pop();
                    }
                    continue;
                }
                
                // Check for block start
                Matcher blockMatcher = BLOCK_START_PATTERN.matcher(line);
                if (blockMatcher.matches()) {
                    String blockName = blockMatcher.group(1);
                    blockStack.push(blockName);
                    continue;
                }
                
                // Check for key-value
                Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(line);
                if (kvMatcher.matches()) {
                    String key = kvMatcher.group(1);
                    String value = kvMatcher.group(2).trim();
                    
                    // Build full key path with blocks
                    String fullKey = buildKeyPath(blockStack, key);
                    
                    // Filter by important fields
                    if (importantFields != null && !importantFields.contains(fullKey)) {
                        continue;
                    }
                    
                    FlattenedConfigItem item = FlattenedConfigItem.builder()
                        .sourceFile(fileName)
                        .fieldKey(fullKey)
                        .value(value)
                        .valueType(detectType(value))
                        .build();
                        
                    items.add(item);
                }
            }
            
            log.info("Parsed {} items from conf file: {}", items.size(), fileName);
            return items;
            
        } catch (Exception e) {
            log.error("Failed to parse conf file: {}", fileName, e);
            throw new ConfigParseException("Invalid conf format: " + e.getMessage(), e);
        }
    }
    
    private String buildKeyPath(Stack<String> blockStack, String key) {
        if (blockStack.isEmpty()) {
            return key;
        }
        
        StringBuilder sb = new StringBuilder();
        for (String block : blockStack) {
            sb.append(block).append(".");
        }
        sb.append(key);
        return sb.toString();
    }
    
    private String detectType(String value) {
        if (value.matches("\\d+")) return "number";
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return "boolean";
        return "string";
    }
    
    @Override
    public boolean supports(String format) {
        return "conf".equalsIgnoreCase(format) || "cfg".equalsIgnoreCase(format);
    }
    
    @Override
    public boolean validate(String fileContent) {
        // Basic validation: check for balanced braces
        int openBraces = 0;
        for (char c : fileContent.toCharArray()) {
            if (c == '{') openBraces++;
            if (c == '}') openBraces--;
        }
        return openBraces == 0;
    }
}
```

---

### 3.5. Service Layer

#### 3.5.1. ConfigFlattenService

```java
package com.nfv.validator.service;

import com.nfv.validator.model.config.*;
import com.nfv.validator.model.cnf.ExtendedCNFChecklistItem;
import com.nfv.validator.service.parser.*;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;

/**
 * Main service for config flattening
 */
@Slf4j
@ApplicationScoped
public class ConfigFlattenService {
    
    @Inject
    ConfigParserFactory parserFactory;
    
    @Inject
    DeploymentTargetValidator targetValidator;
    
    /**
     * Process config flatten request and generate CNF checklist items
     */
    public List<ExtendedCNFChecklistItem> processRequest(ConfigFlattenRequest request) 
        throws ConfigFlattenException {
        
        log.info("Processing config flatten request with {} files", request.getFiles().size());
        
        List<ExtendedCNFChecklistItem> allItems = new ArrayList<>();
        
        for (ConfigFileEntry fileEntry : request.getFiles()) {
            // Validate deployment targets
            for (DeploymentTarget target : fileEntry.getTargets()) {
                targetValidator.validate(target);
            }
            
            // Get appropriate parser
            ConfigParser parser = parserFactory.getParser(fileEntry.getFormat());
            if (parser == null) {
                throw new ConfigFlattenException("Unsupported file format: " + fileEntry.getFormat());
            }
            
            // Parse and flatten config file
            List<FlattenedConfigItem> flattenedItems = parser.parse(
                fileEntry.getFileName(),
                fileEntry.getFileContent(),
                request.getImportantFields()
            );
            
            // Convert to ExtendedCNFChecklistItem for each deployment target
            for (DeploymentTarget target : fileEntry.getTargets()) {
                List<ExtendedCNFChecklistItem> cnfItems = convertToCNFItems(
                    flattenedItems, 
                    request.getVimName(),
                    target
                );
                allItems.addAll(cnfItems);
            }
        }
        
        // Deduplicate if requested
        if (request.isDeduplicateEntries()) {
            allItems = deduplicateItems(allItems);
        }
        
        log.info("Generated {} CNF checklist items (after deduplication)", allItems.size());
        return allItems;
    }
    
    /**
     * Convert flattened config items to CNF checklist items
     */
    private List<ExtendedCNFChecklistItem> convertToCNFItems(
        List<FlattenedConfigItem> flattenedItems,
        String vimName,
        DeploymentTarget target) {
        
        List<ExtendedCNFChecklistItem> cnfItems = new ArrayList<>();
        
        for (FlattenedConfigItem item : flattenedItems) {
            ExtendedCNFChecklistItem cnfItem = new ExtendedCNFChecklistItem();
            
            // Standard CNF fields
            cnfItem.setVimName(vimName);
            cnfItem.setFieldKey(item.getFieldKey());
            cnfItem.setManoValue(item.getValue());
            
            // Extended fields
            cnfItem.setTargetType(target.getTargetType());
            cnfItem.setValueType(item.getValueType());
            cnfItem.setSourceFile(item.getSourceFile());
            
            // Set fields based on target type
            if ("configmap".equals(target.getTargetType()) || "secret".equals(target.getTargetType())) {
                cnfItem.setNamespace(target.getNamespace());
                cnfItem.setObjectName(target.getObjectName());
                cnfItem.setKind("ConfigMap");  // or "Secret"
                
            } else if ("worker-node".equals(target.getTargetType())) {
                cnfItem.setNodeSelector(target.getNodeSelector());
                cnfItem.setObjectName(target.getServiceName());
                cnfItem.setConfigPath(target.getConfigPath());
                cnfItem.setKind("WorkerNode");  // Special kind
            }
            
            cnfItems.add(cnfItem);
        }
        
        return cnfItems;
    }
    
    /**
     * Remove duplicate items
     */
    private List<ExtendedCNFChecklistItem> deduplicateItems(List<ExtendedCNFChecklistItem> items) {
        Set<String> seen = new HashSet<>();
        List<ExtendedCNFChecklistItem> unique = new ArrayList<>();
        
        for (ExtendedCNFChecklistItem item : items) {
            String key = buildUniqueKey(item);
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(item);
            }
        }
        
        log.info("Removed {} duplicate items", items.size() - unique.size());
        return unique;
    }
    
    private String buildUniqueKey(ExtendedCNFChecklistItem item) {
        return String.join("|",
            item.getVimName(),
            item.getTargetType(),
            item.getNamespace() != null ? item.getNamespace() : "",
            item.getObjectName(),
            item.getFieldKey(),
            item.getManoValue()
        );
    }
}
```

#### 3.5.2. ConfigParserFactory

```java
package com.nfv.validator.service.parser;

import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

/**
 * Factory to get appropriate config parser based on file format
 */
@Slf4j
@ApplicationScoped
public class ConfigParserFactory {
    
    @Inject
    YamlConfigParser yamlParser;
    
    @Inject
    JsonConfigParser jsonParser;
    
    @Inject
    PropertiesConfigParser propertiesParser;
    
    @Inject
    ConfConfigParser confParser;
    
    // Optional parsers (P2 priority)
    // @Inject TomlConfigParser tomlParser;
    // @Inject XmlConfigParser xmlParser;
    
    /**
     * Get parser for given format
     */
    public ConfigParser getParser(String format) {
        if (format == null || format.isEmpty()) {
            log.warn("Format is null/empty, defaulting to YAML parser");
            return yamlParser;
        }
        
        List<ConfigParser> parsers = List.of(
            yamlParser,
            jsonParser,
            propertiesParser,
            confParser
        );
        
        for (ConfigParser parser : parsers) {
            if (parser.supports(format)) {
                log.debug("Using parser: {} for format: {}", parser.getClass().getSimpleName(), format);
                return parser;
            }
        }
        
        log.warn("No parser found for format: {}, defaulting to YAML parser", format);
        return yamlParser;
    }
}
```

#### 3.5.3. DeploymentTargetValidator

```java
package com.nfv.validator.service;

import com.nfv.validator.model.config.DeploymentTarget;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;

/**
 * Validator for deployment target configurations
 */
@Slf4j
@ApplicationScoped
public class DeploymentTargetValidator {
    
    public void validate(DeploymentTarget target) throws ValidationException {
        if (target == null) {
            throw new ValidationException("Deployment target cannot be null");
        }
        
        String targetType = target.getTargetType();
        if (targetType == null || targetType.isEmpty()) {
            throw new ValidationException("Target type is required");
        }
        
        switch (targetType.toLowerCase()) {
            case "configmap":
            case "secret":
                validateConfigMapTarget(target);
                break;
                
            case "worker-node":
                validateWorkerNodeTarget(target);
                break;
                
            default:
                throw new ValidationException("Invalid target type: " + targetType + 
                    ". Must be 'configmap', 'secret', or 'worker-node'");
        }
    }
    
    private void validateConfigMapTarget(DeploymentTarget target) throws ValidationException {
        if (target.getNamespace() == null || target.getNamespace().isEmpty()) {
            throw new ValidationException("Namespace is required for ConfigMap/Secret target");
        }
        
        if (target.getObjectName() == null || target.getObjectName().isEmpty()) {
            throw new ValidationException("Object name is required for ConfigMap/Secret target");
        }
        
        // Validate namespace format (lowercase alphanumeric + dash)
        if (!target.getNamespace().matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) {
            throw new ValidationException("Invalid namespace format: " + target.getNamespace());
        }
    }
    
    private void validateWorkerNodeTarget(DeploymentTarget target) throws ValidationException {
        if (target.getNodeSelector() == null || target.getNodeSelector().isEmpty()) {
            throw new ValidationException("Node selector is required for worker-node target");
        }
        
        if (target.getServiceName() == null || target.getServiceName().isEmpty()) {
            throw new ValidationException("Service name is required for worker-node target");
        }
        
        if (target.getConfigPath() == null || target.getConfigPath().isEmpty()) {
            throw new ValidationException("Config path is required for worker-node target");
        }
        
        // Validate node selector format (key=value)
        if (!target.getNodeSelector().matches("^[\\w]+=[\\w-]+$")) {
            throw new ValidationException("Invalid node selector format: " + target.getNodeSelector() + 
                ". Expected format: key=value");
        }
    }
}
```

---

### 3.6. Excel Generator

#### ConfigFlattenExcelGenerator

```java
package com.nfv.validator.service;

import com.nfv.validator.model.cnf.ExtendedCNFChecklistItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generate Excel file for config flatten results
 * Extends CNF Checklist template với additional columns
 */
@Slf4j
@ApplicationScoped
public class ConfigFlattenExcelGenerator {
    
    /**
     * Generate Excel file from extended CNF checklist items
     */
    public byte[] generateExcel(List<ExtendedCNFChecklistItem> items, boolean includeMetadata) 
        throws IOException {
        
        log.info("Generating Excel file with {} items", items.size());
        
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Sheet 1: CNF Checklist (main data)
            createChecklistSheet(workbook, items);
            
            // Sheet 2: Metadata (optional)
            if (includeMetadata) {
                createMetadataSheet(workbook, items);
            }
            
            workbook.write(baos);
            byte[] result = baos.toByteArray();
            
            log.info("Generated Excel file: {} bytes", result.length);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to generate Excel file", e);
            throw new IOException("Failed to generate Excel: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create main checklist sheet
     */
    private void createChecklistSheet(Workbook workbook, List<ExtendedCNFChecklistItem> items) {
        Sheet sheet = workbook.createSheet("CNF Checklist");
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        String[] headers = {
            "VIM Name",
            "Target Type",
            "Namespace",
            "Kind",
            "Object Name",
            "Node Selector",
            "Config Path",
            "Field Key",
            "Expected Value",
            "Value Type",
            "Source File"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        int rowNum = 1;
        for (ExtendedCNFChecklistItem item : items) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(item.getVimName());
            row.createCell(1).setCellValue(item.getTargetType() != null ? item.getTargetType() : "");
            row.createCell(2).setCellValue(item.getNamespace() != null ? item.getNamespace() : "");
            row.createCell(3).setCellValue(item.getKind() != null ? item.getKind() : "");
            row.createCell(4).setCellValue(item.getObjectName());
            row.createCell(5).setCellValue(item.getNodeSelector() != null ? item.getNodeSelector() : "");
            row.createCell(6).setCellValue(item.getConfigPath() != null ? item.getConfigPath() : "");
            row.createCell(7).setCellValue(item.getFieldKey());
            row.createCell(8).setCellValue(item.getManoValue());
            row.createCell(9).setCellValue(item.getValueType() != null ? item.getValueType() : "");
            row.createCell(10).setCellValue(item.getSourceFile() != null ? item.getSourceFile() : "");
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Create metadata sheet with summary information
     */
    private void createMetadataSheet(Workbook workbook, List<ExtendedCNFChecklistItem> items) {
        Sheet sheet = workbook.createSheet("Metadata");
        
        int rowNum = 0;
        
        // Summary statistics
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("Config Flatten Summary");
        
        Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(0).setCellValue("Total Items:");
        totalRow.createCell(1).setCellValue(items.size());
        
        // Count by target type
        long configMapCount = items.stream()
            .filter(i -> "configmap".equals(i.getTargetType()))
            .count();
        long workerNodeCount = items.stream()
            .filter(i -> "worker-node".equals(i.getTargetType()))
            .count();
            
        Row cmRow = sheet.createRow(rowNum++);
        cmRow.createCell(0).setCellValue("ConfigMap Items:");
        cmRow.createCell(1).setCellValue(configMapCount);
        
        Row wnRow = sheet.createRow(rowNum++);
        wnRow.createCell(0).setCellValue("Worker Node Items:");
        wnRow.createCell(1).setCellValue(workerNodeCount);
        
        // Count by source file
        rowNum++;
        Row fileHeaderRow = sheet.createRow(rowNum++);
        fileHeaderRow.createCell(0).setCellValue("Source File");
        fileHeaderRow.createCell(1).setCellValue("Item Count");
        
        items.stream()
            .map(ExtendedCNFChecklistItem::getSourceFile)
            .distinct()
            .sorted()
            .forEach(file -> {
                long count = items.stream()
                    .filter(i -> file.equals(i.getSourceFile()))
                    .count();
                Row fileRow = sheet.createRow(sheet.getLastRowNum() + 1);
                fileRow.createCell(0).setCellValue(file);
                fileRow.createCell(1).setCellValue(count);
            });
        
        // Auto-size
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
```

---

### 3.7. REST API

#### ConfigFlattenResource

```java
package com.nfv.validator.resource;

import com.nfv.validator.model.config.ConfigFlattenRequest;
import com.nfv.validator.model.cnf.ExtendedCNFChecklistItem;
import com.nfv.validator.service.ConfigFlattenService;
import com.nfv.validator.service.ConfigFlattenExcelGenerator;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST API for config flatten feature
 */
@Slf4j
@Path("/kvalidator/api/config-flatten")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigFlattenResource {
    
    @Inject
    ConfigFlattenService configFlattenService;
    
    @Inject
    ConfigFlattenExcelGenerator excelGenerator;
    
    /**
     * Convert config files to CNF checklist Excel
     * 
     * POST /kvalidator/api/config-flatten/convert
     * 
     * Request body: ConfigFlattenRequest
     * Response: Excel file download
     */
    @POST
    @Path("/convert")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response convertToExcel(ConfigFlattenRequest request) {
        log.info("Received config flatten convert request for VIM: {}", request.getVimName());
        
        try {
            // Validate request
            validateRequest(request);
            
            // Process request
            List<ExtendedCNFChecklistItem> items = configFlattenService.processRequest(request);
            
            if (items.isEmpty()) {
                return Response.status(400)
                    .entity("{\"error\": \"No items generated from config files\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            }
            
            // Generate Excel
            byte[] excelBytes = excelGenerator.generateExcel(items, request.isIncludeMetadata());
            
            String fileName = "config-flatten-" + request.getVimName() + ".xlsx";
            
            return Response.ok(excelBytes)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert config files to Excel", e);
            return Response.status(500)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }
    
    /**
     * Preview flattened config items (JSON response)
     * 
     * POST /kvalidator/api/config-flatten/preview
     * 
     * Request body: ConfigFlattenRequest
     * Response: JSON array of ExtendedCNFChecklistItem
     */
    @POST
    @Path("/preview")
    public Response previewItems(ConfigFlattenRequest request) {
        log.info("Received config flatten preview request for VIM: {}", request.getVimName());
        
        try {
            validateRequest(request);
            
            List<ExtendedCNFChecklistItem> items = configFlattenService.processRequest(request);
            
            return Response.ok(items).build();
            
        } catch (Exception e) {
            log.error("Failed to preview config flatten items", e);
            return Response.status(500)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    private void validateRequest(ConfigFlattenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        
        if (request.getVimName() == null || request.getVimName().isEmpty()) {
            throw new IllegalArgumentException("VIM name is required");
        }
        
        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new IllegalArgumentException("At least one config file is required");
        }
        
        // Validate each file entry
        request.getFiles().forEach(file -> {
            if (file.getFileName() == null || file.getFileName().isEmpty()) {
                throw new IllegalArgumentException("File name is required");
            }
            
            if (file.getFileContent() == null || file.getFileContent().isEmpty()) {
                throw new IllegalArgumentException("File content is required for: " + file.getFileName());
            }
            
            if (file.getFormat() == null || file.getFormat().isEmpty()) {
                throw new IllegalArgumentException("File format is required for: " + file.getFileName());
            }
            
            if (file.getTargets() == null || file.getTargets().isEmpty()) {
                throw new IllegalArgumentException("At least one deployment target is required for: " + file.getFileName());
            }
        });
    }
}
```

---

## 4. Frontend Design

### 4.1. Component Structure

```
frontend/src/components/config-flatten/
├── ConfigFlattenPage.tsx          # Main page component
├── FileUploadSection.tsx          # Multiple file upload
├── DeploymentTargetForm.tsx       # Configure deployment targets
├── FieldSelectionPanel.tsx        # Configure field selection
├── PreviewPanel.tsx               # Preview flattened items
└── types.ts                       # TypeScript interfaces
```

### 4.2. ConfigFlattenPage Component

```typescript
// frontend/src/components/config-flatten/ConfigFlattenPage.tsx

import React, { useState } from 'react';
import { FileUploadSection } from './FileUploadSection';
import { DeploymentTargetForm } from './DeploymentTargetForm';
import { FieldSelectionPanel } from './FieldSelectionPanel';
import { PreviewPanel } from './PreviewPanel';
import { ConfigFileEntry, ConfigFlattenRequest } from './types';

export const ConfigFlattenPage: React.FC = () => {
  const [vimName, setVimName] = useState('');
  const [files, setFiles] = useState<ConfigFileEntry[]>([]);
  const [fieldSelectionMode, setFieldSelectionMode] = useState<'full' | 'important' | 'pattern'>('full');
  const [importantFields, setImportantFields] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewItems, setPreviewItems] = useState<any[]>([]);

  const handleFileAdd = (file: ConfigFileEntry) => {
    setFiles([...files, file]);
  };

  const handleFileRemove = (index: number) => {
    setFiles(files.filter((_, i) => i !== index));
  };

  const handlePreview = async () => {
    setLoading(true);
    try {
      const request: ConfigFlattenRequest = {
        vimName,
        files,
        fieldSelectionMode,
        importantFields,
        deduplicateEntries: true,
        includeMetadata: true
      };

      const response = await fetch('/kvalidator/api/config-flatten/preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
      });

      const items = await response.json();
      setPreviewItems(items);
    } catch (error) {
      console.error('Preview failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleConvert = async () => {
    setLoading(true);
    try {
      const request: ConfigFlattenRequest = {
        vimName,
        files,
        fieldSelectionMode,
        importantFields,
        deduplicateEntries: true,
        includeMetadata: true
      };

      const response = await fetch('/kvalidator/api/config-flatten/convert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
      });

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `config-flatten-${vimName}.xlsx`;
      a.click();
    } catch (error) {
      console.error('Convert failed:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="config-flatten-page">
      <h1>Configuration Flattening</h1>
      
      <div className="vim-name-section">
        <label>VIM Name:</label>
        <input 
          type="text" 
          value={vimName} 
          onChange={(e) => setVimName(e.target.value)}
          placeholder="e.g., vim-hanoi"
        />
      </div>

      <FileUploadSection 
        files={files}
        onFileAdd={handleFileAdd}
        onFileRemove={handleFileRemove}
      />

      <FieldSelectionPanel
        mode={fieldSelectionMode}
        onModeChange={setFieldSelectionMode}
        importantFields={importantFields}
        onFieldsChange={setImportantFields}
      />

      <div className="actions">
        <button onClick={handlePreview} disabled={loading || files.length === 0}>
          Preview
        </button>
        <button onClick={handleConvert} disabled={loading || files.length === 0}>
          Convert to Excel
        </button>
      </div>

      {previewItems.length > 0 && (
        <PreviewPanel items={previewItems} />
      )}
    </div>
  );
};
```

---

## 5. Implementation Plan

### Phase 1: Core Infrastructure (Week 1-2)

**Sprint 1.1: Models & Interfaces (3 days)**
- [ ] Create data models (ConfigFlattenRequest, ConfigFileEntry, DeploymentTarget, etc.)
- [ ] Create ConfigParser interface
- [ ] Create exception classes (ConfigParseException, ConfigFlattenException)
- [ ] Unit tests cho models

**Sprint 1.2: Parser Implementations (4 days)**
- [ ] Implement YamlConfigParser (reuse existing logic)
- [ ] Implement JsonConfigParser
- [ ] Implement PropertiesConfigParser
- [ ] Implement ConfConfigParser
- [ ] Unit tests cho mỗi parser
- [ ] Integration test với sample config files

**Sprint 1.3: Service Layer (3 days)**
- [ ] Implement ConfigParserFactory
- [ ] Implement DeploymentTargetValidator
- [ ] Implement ConfigFlattenService
- [ ] Unit tests + integration tests

---

### Phase 2: Excel Generation (Week 3)

**Sprint 2.1: Excel Generator (3 days)**
- [ ] Implement ConfigFlattenExcelGenerator
- [ ] Extended schema với additional columns
- [ ] Metadata sheet generation
- [ ] Unit tests với sample data

**Sprint 2.2: Integration với CNF Checklist (2 days)**
- [ ] Ensure backward compatibility
- [ ] Test Excel upload vào CNF Checklist API
- [ ] Validation integration

---

### Phase 3: REST API (Week 4)

**Sprint 3.1: Backend API (3 days)**
- [ ] Implement ConfigFlattenResource
- [ ] Endpoints: /convert, /preview
- [ ] Request validation
- [ ] Error handling
- [ ] API documentation (Swagger)

**Sprint 3.2: API Testing (2 days)**
- [ ] Postman collection
- [ ] Integration tests
- [ ] Performance testing (large files)

---

### Phase 4: Frontend (Week 5-6)

**Sprint 4.1: Basic UI (4 days)**
- [ ] ConfigFlattenPage component
- [ ] FileUploadSection component
- [ ] DeploymentTargetForm component
- [ ] Basic styling

**Sprint 4.2: Advanced Features (3 days)**
- [ ] FieldSelectionPanel component
- [ ] PreviewPanel component
- [ ] File format auto-detection
- [ ] Drag & drop upload

**Sprint 4.3: UI Polish (3 days)**
- [ ] Responsive design
- [ ] Loading states
- [ ] Error messages
- [ ] Success notifications

---

### Phase 5: Testing & Documentation (Week 7)

**Sprint 5.1: End-to-End Testing (3 days)**
- [ ] E2E test scenarios
- [ ] User acceptance testing
- [ ] Performance testing
- [ ] Bug fixes

**Sprint 5.2: Documentation (2 days)**
- [ ] User guide
- [ ] API documentation
- [ ] Code documentation
- [ ] Migration guide (nếu cần)

---

## 6. Testing Strategy

### 6.1. Unit Tests

**Parser Tests:**
```java
@Test
public void testYamlParser_NestedStructure() {
    String yaml = """
        server:
          port: 8080
          ssl:
            enabled: true
        """;
    
    List<FlattenedConfigItem> items = yamlParser.parse("test.yaml", yaml, null);
    
    assertEquals(2, items.size());
    assertTrue(items.stream().anyMatch(i -> 
        "server.port".equals(i.getFieldKey()) && "8080".equals(i.getValue())
    ));
    assertTrue(items.stream().anyMatch(i -> 
        "server.ssl.enabled".equals(i.getFieldKey()) && "true".equals(i.getValue())
    ));
}

@Test
public void testYamlParser_ArrayIndexing() {
    String yaml = """
        servers:
          - host: db1.example.com
            port: 5432
          - host: db2.example.com
            port: 5432
        """;
    
    List<FlattenedConfigItem> items = yamlParser.parse("test.yaml", yaml, null);
    
    assertTrue(items.stream().anyMatch(i -> 
        "servers[0].host".equals(i.getFieldKey()) && "db1.example.com".equals(i.getValue())
    ));
}
```

**Service Tests:**
```java
@Test
public void testConfigFlattenService_MultipleFiles() {
    ConfigFlattenRequest request = new ConfigFlattenRequest();
    request.setVimName("vim-test");
    // ... setup files and targets
    
    List<ExtendedCNFChecklistItem> items = configFlattenService.processRequest(request);
    
    assertFalse(items.isEmpty());
    assertTrue(items.stream().allMatch(i -> "vim-test".equals(i.getVimName())));
}
```

### 6.2. Integration Tests

```java
@QuarkusTest
public class ConfigFlattenResourceTest {
    
    @Test
    public void testConvertToExcel_Success() {
        ConfigFlattenRequest request = buildSampleRequest();
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/kvalidator/api/config-flatten/convert")
        .then()
            .statusCode(200)
            .header("Content-Disposition", containsString("attachment"))
            .header("Content-Type", containsString("spreadsheet"));
    }
}
```

### 6.3. Performance Tests

```java
@Test
public void testPerformance_LargeConfigFile() {
    // Generate large YAML file (1000 fields)
    String largeYaml = generateLargeYaml(1000);
    
    long startTime = System.currentTimeMillis();
    List<FlattenedConfigItem> items = yamlParser.parse("large.yaml", largeYaml, null);
    long duration = System.currentTimeMillis() - startTime;
    
    assertEquals(1000, items.size());
    assertTrue(duration < 500, "Parsing should complete in < 500ms"); // Performance requirement
}
```

---

## 7. Success Metrics

### 7.1. Functional Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Supported formats | 4+ formats | YAML, JSON, Properties, CONF |
| Parse success rate | > 95% | Valid files parsed successfully |
| Excel compatibility | 100% | All generated Excel files can be uploaded to CNF Checklist API |
| Field extraction accuracy | > 99% | Correct flattening of nested structures |

### 7.2. Performance Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Parse time (10KB file) | < 100ms | TBD | 🟡 To be measured |
| Excel generation (1000 items) | < 2s | TBD | 🟡 To be measured |
| Batch processing (50 files) | < 30s | TBD | 🟡 To be measured |
| Memory usage | < 512MB | TBD | 🟡 To be measured |

### 7.3. User Experience Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Time to convert (end-to-end) | < 1 minute | User testing |
| Error rate | < 5% | Error tracking |
| User satisfaction | > 4/5 | Survey |

---

## 8. Risks & Mitigation

### 8.1. Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Parser complexity for .conf files | High | Medium | Start with basic key-value, iterate |
| Performance issues với large files | Medium | Medium | Implement streaming, size limits |
| Excel format incompatibility | High | Low | Extensive testing với CNF Checklist API |
| Memory issues với batch processing | Medium | Medium | Process files sequentially, configurable limits |

### 8.2. Integration Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Breaking CNF Checklist workflow | High | Low | Thorough backward compatibility testing |
| K8s API validation fails | Medium | Low | Validate targets before processing |
| Frontend-backend contract mismatch | Medium | Medium | API-first design, shared TypeScript types |

---

## 9. Future Enhancements (Post-MVP)

### 9.1. Phase 2 Features (P2)

**Advanced Parsing:**
- [ ] TOML format support
- [ ] XML format support
- [ ] INI format support
- [ ] Custom parser plugins

**Smart Features:**
- [ ] Auto-detect file format from content
- [ ] Smart field suggestions (ML-based)
- [ ] Configuration drift detection
- [ ] Template library (common configs)

**Integration:**
- [ ] Direct ConfigMap fetch from K8s cluster
- [ ] SSH to worker nodes để fetch configs
- [ ] Git integration (fetch configs from repo)
- [ ] Compare configs across environments

### 9.2. Phase 3 Features (Future)

**Validation Enhancement:**
- [ ] Custom validation rules per field type
- [ ] Range validation (min/max for numbers)
- [ ] Regex validation for strings
- [ ] Cross-field dependencies

**Reporting:**
- [ ] Configuration coverage reports
- [ ] Change tracking over time
- [ ] Compliance reporting
- [ ] Audit logs

**Automation:**
- [ ] Schedule automatic config extraction
- [ ] CI/CD integration
- [ ] Webhook notifications
- [ ] Auto-remediation

---

## 10. Appendix

### 10.1. Sample Request Examples

#### Example 1: Nginx Config on Worker Nodes

```json
{
  "vimName": "vim-hanoi",
  "files": [
    {
      "fileName": "nginx.conf",
      "fileContent": "worker_processes 4;\nevents {\n    worker_connections 1024;\n}\nhttp {\n    server {\n        listen 80;\n        server_name example.com;\n    }\n}",
      "format": "conf",
      "targets": [
        {
          "targetType": "worker-node",
          "nodeSelector": "role=nginx",
          "serviceName": "nginx",
          "configPath": "/etc/nginx/nginx.conf"
        }
      ]
    }
  ],
  "fieldSelectionMode": "full",
  "deduplicateEntries": true,
  "includeMetadata": true
}
```

#### Example 2: Application Config in ConfigMap

```json
{
  "vimName": "kind-kind-infra-test",
  "files": [
    {
      "fileName": "application.yaml",
      "fileContent": "server:\n  port: 8080\n  ssl:\n    enabled: true\ndatabase:\n  url: jdbc:postgresql://localhost:5432/mydb\n  username: admin",
      "format": "yaml",
      "targets": [
        {
          "targetType": "configmap",
          "namespace": "production",
          "objectName": "app-config",
          "mountPath": "/config/application.yaml"
        }
      ]
    }
  ],
  "fieldSelectionMode": "important",
  "importantFields": [
    "server.port",
    "server.ssl.enabled",
    "database.*"
  ]
}
```

#### Example 3: Multi-File Batch

```json
{
  "vimName": "vim-hanoi",
  "files": [
    {
      "fileName": "haproxy.cfg",
      "format": "conf",
      "fileContent": "...",
      "targets": [
        {
          "targetType": "worker-node",
          "nodeSelector": "role=lb",
          "serviceName": "haproxy",
          "configPath": "/etc/haproxy/haproxy.cfg"
        }
      ]
    },
    {
      "fileName": "database.properties",
      "format": "properties",
      "fileContent": "...",
      "targets": [
        {
          "targetType": "configmap",
          "namespace": "production",
          "objectName": "db-config"
        }
      ]
    },
    {
      "fileName": "settings.json",
      "format": "json",
      "fileContent": "...",
      "targets": [
        {
          "targetType": "configmap",
          "namespace": "staging",
          "objectName": "app-settings"
        }
      ]
    }
  ]
}
```

### 10.2. Excel Output Sample

| VIM Name | Target Type | Namespace | Kind | Object Name | Node Selector | Config Path | Field Key | Expected Value | Value Type | Source File |
|----------|-------------|-----------|------|-------------|---------------|-------------|-----------|----------------|------------|-------------|
| vim-hanoi | worker-node | | WorkerNode | nginx | role=nginx | /etc/nginx/nginx.conf | worker_processes | 4 | number | nginx.conf |
| vim-hanoi | worker-node | | WorkerNode | nginx | role=nginx | /etc/nginx/nginx.conf | events.worker_connections | 1024 | number | nginx.conf |
| vim-hanoi | worker-node | | WorkerNode | nginx | role=nginx | /etc/nginx/nginx.conf | http.server.listen | 80 | number | nginx.conf |
| kind-test | configmap | production | ConfigMap | app-config | | | server.port | 8080 | number | application.yaml |
| kind-test | configmap | production | ConfigMap | app-config | | | server.ssl.enabled | true | boolean | application.yaml |

---

## Conclusion

Tính năng **Configuration Flattening** sẽ mở rộng đáng kể khả năng của KValidator, cho phép validate không chỉ Kubernetes resources mà còn cả application configuration files từ nhiều nguồn khác nhau. 

**Key Differentiators:**
✅ Multi-format support (YAML, JSON, Properties, CONF)
✅ Deployment target awareness (ConfigMap vs Worker Node)
✅ Smart field selection và pattern matching
✅ Seamless integration với CNF Checklist workflow
✅ Excel template standardization

**Business Impact:**
- Tăng coverage validation từ infrastructure → application level
- Chuẩn hóa quy trình quản lý cấu hình
- Hỗ trợ compliance và audit requirements
- Giảm manual effort trong configuration management

Implementation sẽ tuân theo kiến trúc modular hiện có, đảm bảo backward compatibility và dễ dàng mở rộng trong tương lai.

---

## 11. Data Validation Enhancement - Deep ConfigMap/Secret Comparison

### 11.1. Problem Analysis

**Current State:**
- CNF Checklist validation hỗ trợ validate **spec fields** của resources (e.g., `spec.replicas`, `spec.template.spec.containers[0].image`)
- ConfigMap/Secret có cấu trúc đặc biệt với **data layer**:
  ```yaml
  apiVersion: v1
  kind: ConfigMap
  metadata:
    name: nginx-config
    namespace: production
  data:
    nginx.conf: |
      worker_processes 4;
      events {
        worker_connections 1024;
      }
    application.yaml: |
      server:
        port: 8080
        ssl:
          enabled: true
  ```
- Data values có thể là:
  - Simple strings: `data.version: "1.0.0"`
  - Complex configs: `data.nginx.conf` (chứa entire nginx config)
  - Multi-line YAML/JSON: `data.application.yaml`

**Gap:**
- Hiện tại chỉ validate được `data.<key>` như simple string comparison
- Không thể deep compare nội dung bên trong data values (nested configs)
- Không có cách flatten ConfigMap data để validate từng field cụ thể

**Use Cases:**

| Scenario | Current Support | Desired Support |
|----------|----------------|-----------------|
| Validate `data.version` = "1.0.0" | ✅ Supported | ✅ Keep existing |
| Validate `data.nginx.conf` contains "worker_processes 4" | ❌ No | ✅ Need this |
| Validate `data.application.yaml` → `server.port` = 8080 | ❌ No | ✅ Need this |
| Compare nginx config between prod vs staging | ❌ No | ✅ Need this |

---

### 11.2. Solution Design - Hybrid Approach

Tôi đề xuất **Approach 3: Unified Multi-Mode Validation** - Kết hợp cả 2 approaches với auto-detection:

```
┌─────────────────────────────────────────────────────────────────────┐
│                   CNF Checklist Validation                          │
│                     (Single Entry Point)                             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │  Auto-Detect Mode    │
                  │  Based on Field Key  │
                  └──────────┬───────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
    ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐
    │ SPEC MODE   │  │ DATA MODE   │  │ DATA-FLATTEN    │
    │ (existing)  │  │ (simple)    │  │ MODE (new)      │
    └─────────────┘  └─────────────┘  └─────────────────┘
    
    spec.replicas   data.version    data.nginx.conf::
                    data.key1       worker_processes
                                    
                                    data.app.yaml::
                                    server.port
```

**Validation Modes:**

| Mode | Field Key Pattern | Example | Behavior |
|------|------------------|---------|----------|
| **SPEC** | `spec.*` | `spec.replicas` | Standard spec validation (existing) |
| **METADATA** | `metadata.*` | `metadata.labels.app` | Metadata validation (existing) |
| **DATA-SIMPLE** | `data.<key>` | `data.version` | Simple string comparison (existing) |
| **DATA-FLATTEN** | `data.<key>::<nested-path>` | `data.nginx.conf::worker_processes` | Deep config validation (NEW) |

**Key Innovation: Double-Colon Syntax (`::`)**
- `::` separates ConfigMap data key from nested config path
- Example: `data.application.yaml::server.ssl.enabled`
  - `data.application.yaml` = ConfigMap data key
  - `server.ssl.enabled` = path inside YAML content

---

### 11.3. Technical Design

#### 11.3.1. Enhanced CNFChecklistItem Model

```java
@Data
public class CNFChecklistItem {
    private String vimName;
    private String namespace;
    private String kind;
    private String objectName;
    private String fieldKey;           // Can contain :: for data-flatten mode
    private String manoValue;
    
    // NEW: Auto-detected validation mode
    private transient ValidationMode validationMode;
    
    // NEW: Parsed components for data-flatten mode
    private transient String dataKey;           // e.g., "nginx.conf"
    private transient String nestedPath;        // e.g., "worker_processes"
    private transient String detectedFormat;    // e.g., "conf", "yaml", "json"
}

enum ValidationMode {
    SPEC,           // spec.* fields
    METADATA,       // metadata.* fields
    DATA_SIMPLE,    // data.<key> without ::
    DATA_FLATTEN    // data.<key>::<nested-path>
}
```

#### 11.3.2. Field Key Parser

```java
package com.nfv.validator.service;

import com.nfv.validator.model.cnf.CNFChecklistItem;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;

/**
 * Parse and classify CNF checklist field keys
 */
@Slf4j
@ApplicationScoped
public class FieldKeyParser {
    
    private static final String DATA_FLATTEN_SEPARATOR = "::";
    
    /**
     * Parse field key and detect validation mode
     */
    public void parseAndClassify(CNFChecklistItem item) {
        String fieldKey = item.getFieldKey();
        
        // Detect mode based on field key pattern
        if (fieldKey.startsWith("spec.")) {
            item.setValidationMode(ValidationMode.SPEC);
            
        } else if (fieldKey.startsWith("metadata.")) {
            item.setValidationMode(ValidationMode.METADATA);
            
        } else if (fieldKey.startsWith("data.")) {
            if (fieldKey.contains(DATA_FLATTEN_SEPARATOR)) {
                // Data-Flatten mode: data.<key>::<nested-path>
                parseDataFlattenKey(item);
            } else {
                // Data-Simple mode: data.<key>
                item.setValidationMode(ValidationMode.DATA_SIMPLE);
                String dataKey = fieldKey.substring("data.".length());
                item.setDataKey(dataKey);
            }
            
        } else {
            // Default to SPEC mode
            item.setValidationMode(ValidationMode.SPEC);
        }
        
        log.debug("Parsed field key '{}' -> mode: {}", fieldKey, item.getValidationMode());
    }
    
    /**
     * Parse data-flatten field key
     * Example: "data.nginx.conf::worker_processes" 
     *   -> dataKey="nginx.conf", nestedPath="worker_processes"
     */
    private void parseDataFlattenKey(CNFChecklistItem item) {
        String fieldKey = item.getFieldKey();
        
        // Remove "data." prefix
        String afterData = fieldKey.substring("data.".length());
        
        // Split by ::
        String[] parts = afterData.split(DATA_FLATTEN_SEPARATOR, 2);
        
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid data-flatten field key: " + fieldKey + 
                ". Expected format: data.<key>::<nested-path>"
            );
        }
        
        String dataKey = parts[0];
        String nestedPath = parts[1];
        
        item.setValidationMode(ValidationMode.DATA_FLATTEN);
        item.setDataKey(dataKey);
        item.setNestedPath(nestedPath);
        
        // Auto-detect format from file extension
        item.setDetectedFormat(detectFormat(dataKey));
        
        log.debug("Data-flatten: key='{}', path='{}', format='{}'", 
                 dataKey, nestedPath, item.getDetectedFormat());
    }
    
    /**
     * Detect config format from data key
     */
    private String detectFormat(String dataKey) {
        String lower = dataKey.toLowerCase();
        
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "yaml";
        } else if (lower.endsWith(".json")) {
            return "json";
        } else if (lower.endsWith(".properties") || lower.endsWith(".props")) {
            return "properties";
        } else if (lower.endsWith(".conf") || lower.endsWith(".cfg")) {
            return "conf";
        } else if (lower.endsWith(".toml")) {
            return "toml";
        } else if (lower.endsWith(".xml")) {
            return "xml";
        } else {
            // Default to plain text
            return "text";
        }
    }
}
```

#### 11.3.3. Data Flatten Validator

```java
package com.nfv.validator.service;

import com.nfv.validator.model.cnf.CNFChecklistItem;
import com.nfv.validator.service.parser.ConfigParser;
import com.nfv.validator.service.parser.ConfigParserFactory;
import com.nfv.validator.model.config.FlattenedConfigItem;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Validate ConfigMap/Secret data with deep flattening
 */
@Slf4j
@ApplicationScoped
public class DataFlattenValidator {
    
    @Inject
    ConfigParserFactory parserFactory;
    
    /**
     * Extract and validate nested value from ConfigMap data
     * 
     * @param dataContent Content of data.<key> from ConfigMap
     * @param nestedPath Path to nested field (e.g., "server.port")
     * @param format Config format (yaml, json, conf, etc.)
     * @return Actual value at nested path, or null if not found
     */
    public String extractNestedValue(String dataContent, String nestedPath, String format) 
        throws Exception {
        
        log.debug("Extracting nested value from {} config, path: {}", format, nestedPath);
        
        // Get appropriate parser
        ConfigParser parser = parserFactory.getParser(format);
        if (parser == null) {
            log.warn("No parser found for format: {}, treating as plain text", format);
            return null;
        }
        
        // Parse config content
        List<FlattenedConfigItem> flattenedItems = parser.parse(
            "data-content",  // dummy file name
            dataContent,
            null  // extract all fields
        );
        
        // Find matching field
        for (FlattenedConfigItem item : flattenedItems) {
            if (item.getFieldKey().equals(nestedPath)) {
                log.debug("Found nested value: {} = {}", nestedPath, item.getValue());
                return item.getValue();
            }
        }
        
        log.warn("Nested path not found: {}", nestedPath);
        return null;
    }
    
    /**
     * Validate data-flatten field
     */
    public boolean validateDataFlatten(CNFChecklistItem checklistItem, 
                                        Map<String, String> actualConfigMapData) {
        
        String dataKey = checklistItem.getDataKey();
        String nestedPath = checklistItem.getNestedPath();
        String expectedValue = checklistItem.getManoValue();
        
        // Get data content from ConfigMap
        String dataContent = actualConfigMapData.get(dataKey);
        if (dataContent == null) {
            log.warn("Data key '{}' not found in ConfigMap", dataKey);
            return false;
        }
        
        try {
            // Extract nested value
            String actualValue = extractNestedValue(
                dataContent, 
                nestedPath, 
                checklistItem.getDetectedFormat()
            );
            
            if (actualValue == null) {
                log.warn("Nested path '{}' not found in data.{}", nestedPath, dataKey);
                return false;
            }
            
            // Normalize and compare
            String normalizedExpected = normalizeValue(expectedValue);
            String normalizedActual = normalizeValue(actualValue);
            
            boolean matches = normalizedExpected.equals(normalizedActual);
            
            log.debug("Data-flatten validation: {} = {} (expected: {})", 
                     nestedPath, actualValue, expectedValue);
            
            return matches;
            
        } catch (Exception e) {
            log.error("Failed to validate data-flatten field: {}", checklistItem.getFieldKey(), e);
            return false;
        }
    }
    
    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.trim();
    }
}
```

#### 11.3.4. Integration with Existing Comparison Flow

```java
// In NamespaceComparator.java

@Inject
DataFlattenValidator dataFlattenValidator;

@Inject
FieldKeyParser fieldKeyParser;

/**
 * Compare CNF checklist item (enhanced with data-flatten support)
 */
private FieldComparison compareField(CNFChecklistItem item, 
                                     Map<String, Object> actualObject) {
    
    // Parse and classify field key
    fieldKeyParser.parseAndClassify(item);
    
    // Route to appropriate validator based on mode
    switch (item.getValidationMode()) {
        case SPEC:
        case METADATA:
            // Existing logic
            return compareStandardField(item, actualObject);
            
        case DATA_SIMPLE:
            // Existing simple data comparison
            return compareSimpleDataField(item, actualObject);
            
        case DATA_FLATTEN:
            // NEW: Deep data validation
            return compareDataFlattenField(item, actualObject);
            
        default:
            throw new IllegalStateException("Unknown validation mode: " + item.getValidationMode());
    }
}

/**
 * Compare data-flatten field (NEW)
 */
private FieldComparison compareDataFlattenField(CNFChecklistItem item, 
                                                Map<String, Object> actualObject) {
    
    FieldComparison comparison = new FieldComparison();
    comparison.setFieldKey(item.getFieldKey());
    comparison.setExpectedValue(item.getManoValue());
    
    // Extract data map from ConfigMap/Secret
    @SuppressWarnings("unchecked")
    Map<String, String> dataMap = (Map<String, String>) actualObject.get("data");
    
    if (dataMap == null) {
        comparison.setMatch(false);
        comparison.setActualValue(null);
        comparison.setMessage("ConfigMap/Secret has no data section");
        return comparison;
    }
    
    // Validate using DataFlattenValidator
    boolean matches = dataFlattenValidator.validateDataFlatten(item, dataMap);
    
    // Get actual nested value for display
    String dataContent = dataMap.get(item.getDataKey());
    String actualValue = null;
    try {
        actualValue = dataFlattenValidator.extractNestedValue(
            dataContent,
            item.getNestedPath(),
            item.getDetectedFormat()
        );
    } catch (Exception e) {
        log.warn("Failed to extract actual value for display", e);
    }
    
    comparison.setMatch(matches);
    comparison.setActualValue(actualValue);
    comparison.setMessage(matches ? "Match" : "Value mismatch");
    
    return comparison;
}
```

---

### 11.4. User Experience Design

#### 11.4.1. CNF Checklist Input Examples

**Example 1: Simple Data Validation (Existing)**
```json
{
  "items": [
    {
      "vimName": "vim-hanoi",
      "kind": "ConfigMap",
      "namespace": "production",
      "objectName": "app-config",
      "fieldKey": "data.version",
      "manoValue": "1.0.0"
    }
  ]
}
```

**Example 2: Data-Flatten Validation (NEW)**
```json
{
  "items": [
    {
      "vimName": "vim-hanoi",
      "kind": "ConfigMap",
      "namespace": "production",
      "objectName": "nginx-config",
      "fieldKey": "data.nginx.conf::worker_processes",
      "manoValue": "4"
    },
    {
      "vimName": "vim-hanoi",
      "kind": "ConfigMap",
      "namespace": "production",
      "objectName": "nginx-config",
      "fieldKey": "data.nginx.conf::events.worker_connections",
      "manoValue": "1024"
    },
    {
      "vimName": "vim-hanoi",
      "kind": "ConfigMap",
      "namespace": "production",
      "objectName": "app-settings",
      "fieldKey": "data.application.yaml::server.port",
      "manoValue": "8080"
    },
    {
      "vimName": "vim-hanoi",
      "kind": "ConfigMap",
      "namespace": "production",
      "objectName": "app-settings",
      "fieldKey": "data.application.yaml::server.ssl.enabled",
      "manoValue": "true"
    }
  ]
}
```

**Example 3: Mixed Validation**
```json
{
  "items": [
    {
      "kind": "Deployment",
      "fieldKey": "spec.replicas",
      "manoValue": "3"
    },
    {
      "kind": "ConfigMap",
      "fieldKey": "data.version",
      "manoValue": "1.0.0"
    },
    {
      "kind": "ConfigMap",
      "fieldKey": "data.nginx.conf::worker_processes",
      "manoValue": "4"
    }
  ]
}
```

#### 11.4.2. Excel Template Enhancement

**Updated Column Schema:**

| VIM Name | Namespace | Kind | Object Name | Field Key | Expected Value | Validation Mode | Notes |
|----------|-----------|------|-------------|-----------|----------------|-----------------|-------|
| vim-hanoi | elk | Deployment | elasticsearch | spec.replicas | 2 | SPEC | Standard |
| vim-hanoi | elk | ConfigMap | nginx-config | data.version | 1.0.0 | DATA_SIMPLE | Simple string |
| vim-hanoi | elk | ConfigMap | nginx-config | data.nginx.conf::worker_processes | 4 | DATA_FLATTEN | Deep validation |
| vim-hanoi | elk | ConfigMap | app-settings | data.application.yaml::server.port | 8080 | DATA_FLATTEN | YAML nested |

**Auto-detection trong Excel:**
- User chỉ cần nhập `fieldKey` đúng format
- System tự detect validation mode
- Optional: "Validation Mode" column để user hiểu rõ

#### 11.4.3. Results Display

**Enhanced Validation Results:**

```
┌─────────────────────────────────────────────────────────────────┐
│  CNF Checklist Validation Results                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  📊 Summary                                                      │
│  ├─ Total Items: 10                                             │
│  ├─ Spec Validations: 5 (✅ 5 passed)                           │
│  ├─ Data-Simple Validations: 2 (✅ 2 passed)                    │
│  └─ Data-Flatten Validations: 3 (✅ 2 passed, ❌ 1 failed)      │
│                                                                  │
│  📋 Detailed Results                                             │
│                                                                  │
│  ✅ SPEC: spec.replicas = 2                                     │
│     Expected: 2, Actual: 2                                      │
│                                                                  │
│  ✅ DATA-SIMPLE: data.version = 1.0.0                           │
│     Expected: 1.0.0, Actual: 1.0.0                              │
│                                                                  │
│  ✅ DATA-FLATTEN: data.nginx.conf::worker_processes = 4         │
│     ConfigMap: nginx-config                                     │
│     Data Key: nginx.conf                                        │
│     Nested Path: worker_processes                               │
│     Expected: 4, Actual: 4                                      │
│     Format: conf                                                │
│                                                                  │
│  ❌ DATA-FLATTEN: data.application.yaml::server.ssl.enabled     │
│     ConfigMap: app-settings                                     │
│     Data Key: application.yaml                                  │
│     Nested Path: server.ssl.enabled                             │
│     Expected: true, Actual: false                               │
│     Format: yaml                                                │
│     💡 Hint: Check SSL configuration in ConfigMap              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Excel Report Enhancement:**

Add new sheet: **"Data Validation Details"**

| Object Name | Data Key | Nested Path | Expected | Actual | Match | Format | Full Data Preview |
|-------------|----------|-------------|----------|--------|-------|--------|-------------------|
| nginx-config | nginx.conf | worker_processes | 4 | 4 | ✅ | conf | worker_processes 4;<br>events { ... } |
| app-settings | application.yaml | server.port | 8080 | 8080 | ✅ | yaml | server:<br>  port: 8080<br>  ssl: ... |
| app-settings | application.yaml | server.ssl.enabled | true | false | ❌ | yaml | ... |

---

### 11.5. Frontend Integration

#### 11.5.1. Enhanced CNF Checklist Table Input

```typescript
// Add helper UI for data-flatten field keys

interface FieldKeyBuilder {
  mode: 'spec' | 'metadata' | 'data-simple' | 'data-flatten';
  dataKey?: string;
  nestedPath?: string;
}

const FieldKeyInput: React.FC = () => {
  const [mode, setMode] = useState<'spec' | 'data-simple' | 'data-flatten'>('spec');
  const [dataKey, setDataKey] = useState('');
  const [nestedPath, setNestedPath] = useState('');
  
  const buildFieldKey = () => {
    if (mode === 'spec') {
      return `spec.${/* user input */}`;
    } else if (mode === 'data-simple') {
      return `data.${dataKey}`;
    } else if (mode === 'data-flatten') {
      return `data.${dataKey}::${nestedPath}`;
    }
  };
  
  return (
    <div className="field-key-builder">
      <select value={mode} onChange={(e) => setMode(e.target.value)}>
        <option value="spec">Spec Field</option>
        <option value="data-simple">Data (Simple)</option>
        <option value="data-flatten">Data (Deep Validation)</option>
      </select>
      
      {mode === 'data-flatten' && (
        <>
          <input 
            placeholder="Data key (e.g., nginx.conf)"
            value={dataKey}
            onChange={(e) => setDataKey(e.target.value)}
          />
          <input 
            placeholder="Nested path (e.g., worker_processes)"
            value={nestedPath}
            onChange={(e) => setNestedPath(e.target.value)}
          />
          <div className="preview">
            Field Key: <code>{buildFieldKey()}</code>
          </div>
        </>
      )}
    </div>
  );
};
```

#### 11.5.2. ConfigMap Data Explorer (Optional Enhancement)

```typescript
/**
 * Interactive explorer to browse ConfigMap data and generate field keys
 */
const ConfigMapDataExplorer: React.FC = ({ vimName, namespace, configMapName }) => {
  const [configMapData, setConfigMapData] = useState<Map<string, string>>({});
  const [selectedKey, setSelectedKey] = useState('');
  const [flattenedFields, setFlattenedFields] = useState<FlattenedConfigItem[]>([]);
  
  useEffect(() => {
    // Fetch ConfigMap from K8s
    fetchConfigMap(vimName, namespace, configMapName).then(cm => {
      setConfigMapData(cm.data);
    });
  }, [vimName, namespace, configMapName]);
  
  const handleDataKeySelect = (dataKey: string) => {
    setSelectedKey(dataKey);
    
    // Auto-flatten selected data key
    const dataContent = configMapData[dataKey];
    const format = detectFormat(dataKey);
    
    flattenConfigData(dataContent, format).then(fields => {
      setFlattenedFields(fields);
    });
  };
  
  return (
    <div className="configmap-explorer">
      <h3>ConfigMap: {configMapName}</h3>
      
      {/* Data keys list */}
      <div className="data-keys">
        {Object.keys(configMapData).map(key => (
          <button key={key} onClick={() => handleDataKeySelect(key)}>
            {key}
          </button>
        ))}
      </div>
      
      {/* Flattened fields */}
      {selectedKey && (
        <div className="flattened-fields">
          <h4>Fields in {selectedKey}:</h4>
          <table>
            <thead>
              <tr>
                <th>Nested Path</th>
                <th>Current Value</th>
                <th>Add to Checklist</th>
              </tr>
            </thead>
            <tbody>
              {flattenedFields.map(field => (
                <tr key={field.fieldKey}>
                  <td><code>{field.fieldKey}</code></td>
                  <td>{field.value}</td>
                  <td>
                    <button onClick={() => addToChecklist(
                      `data.${selectedKey}::${field.fieldKey}`,
                      field.value
                    )}>
                      ➕ Add
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
```

---

### 11.6. Backward Compatibility

**Compatibility Matrix:**

| Field Key Format | Old Behavior | New Behavior | Compatible? |
|------------------|--------------|--------------|-------------|
| `spec.replicas` | SPEC validation | SPEC validation | ✅ Yes |
| `metadata.labels.app` | METADATA validation | METADATA validation | ✅ Yes |
| `data.version` | DATA_SIMPLE validation | DATA_SIMPLE validation | ✅ Yes |
| `data.nginx.conf::worker_processes` | ❌ Not supported | DATA_FLATTEN validation | ✅ Yes (new) |

**Migration Path:**
1. **No breaking changes** - existing field keys work as before
2. **Opt-in enhancement** - users can start using `::` syntax when needed
3. **Gradual adoption** - can mix old and new formats in same request

**Version Detection:**
```java
// In CNFChecklistService
public void processRequest(CNFChecklistRequest request) {
    boolean usesDataFlatten = request.getItems().stream()
        .anyMatch(item -> item.getFieldKey().contains("::"));
    
    if (usesDataFlatten) {
        log.info("Request uses DATA-FLATTEN mode (enhanced validation)");
    }
    
    // Process normally - auto-detection handles both modes
}
```

---

### 11.7. Implementation Plan - Data Validation Enhancement

**Phase 1: Core Infrastructure (Week 1)**
- [ ] Create `FieldKeyParser` service
- [ ] Add `ValidationMode` enum
- [ ] Extend `CNFChecklistItem` model
- [ ] Unit tests for field key parsing

**Phase 2: Data Flatten Validator (Week 2)**
- [ ] Create `DataFlattenValidator` service
- [ ] Integrate with `ConfigParserFactory`
- [ ] Unit tests with sample ConfigMaps
- [ ] Integration tests

**Phase 3: Comparison Integration (Week 3)**
- [ ] Update `NamespaceComparator` with data-flatten support
- [ ] Add routing logic based on validation mode
- [ ] Update result models
- [ ] Integration tests

**Phase 4: Frontend Enhancement (Week 4)**
- [ ] Field key builder UI component
- [ ] ConfigMap data explorer (optional)
- [ ] Enhanced results display
- [ ] Excel template updates

**Phase 5: Documentation & Testing (Week 5)**
- [ ] User guide with examples
- [ ] API documentation updates
- [ ] E2E testing
- [ ] Performance testing

---

### 11.8. Comparison: Approach Analysis

**Approach 1: Extend CNF Checklist (Recommended)**

✅ **Pros:**
- Single unified validation flow
- Reuse existing UI and API
- Natural extension of current model
- Easier for users (one place for all validations)

❌ **Cons:**
- Field key syntax might be confusing initially (`::`)
- Mixed validation types in same result set

**Approach 2: Separate Feature**

✅ **Pros:**
- Clear separation of concerns
- Dedicated UI can be more specialized
- Independent evolution

❌ **Cons:**
- Duplicate code (parsing, comparison, reporting)
- Users need to learn 2 separate workflows
- More maintenance overhead
- Data duplication if validating ConfigMap spec + data

**Approach 3: Hybrid (Chosen)**

✅ **Pros:**
- Best of both worlds
- Backward compatible
- Auto-detection eliminates confusion
- Unified reporting with mode awareness

---

### 11.9. Advanced Use Cases

#### Use Case 1: Multi-Environment Config Drift Detection

```json
{
  "items": [
    {
      "vimName": "vim-prod",
      "namespace": "production",
      "kind": "ConfigMap",
      "objectName": "nginx-config",
      "fieldKey": "data.nginx.conf::worker_processes",
      "manoValue": "8"
    },
    {
      "vimName": "vim-staging",
      "namespace": "staging",
      "kind": "ConfigMap",
      "objectName": "nginx-config",
      "fieldKey": "data.nginx.conf::worker_processes",
      "manoValue": "8"
    }
  ]
}
```

Result shows if both environments have same worker_processes config.

#### Use Case 2: Compliance Validation

```json
{
  "items": [
    {
      "kind": "ConfigMap",
      "objectName": "app-config",
      "fieldKey": "data.application.yaml::security.tls.enabled",
      "manoValue": "true"
    },
    {
      "kind": "ConfigMap",
      "objectName": "app-config",
      "fieldKey": "data.application.yaml::security.tls.minVersion",
      "manoValue": "1.2"
    }
  ]
}
```

Ensure security compliance across all ConfigMaps.

#### Use Case 3: Database Config Validation

```json
{
  "items": [
    {
      "kind": "ConfigMap",
      "objectName": "db-config",
      "fieldKey": "data.database.properties::connection.pool.size",
      "manoValue": "20"
    },
    {
      "kind": "Secret",
      "objectName": "db-secret",
      "fieldKey": "data.config.json::username",
      "manoValue": "admin"
    }
  ]
}
```

---

### 11.10. Future Enhancements

**Phase 2:**
- [ ] Support regex matching for nested values
- [ ] Wildcard support: `data.*.yaml::server.port` (all YAML files)
- [ ] Array element matching: `data.servers.json::[0].host`
- [ ] Conditional validation: "if SSL enabled, then check certificate"

**Phase 3:**
- [ ] AI-powered config suggestion
- [ ] Config template library
- [ ] Auto-fix recommendations
- [ ] Visual diff for nested configs

---

**Document Version:** 1.1  
**Last Updated:** January 26, 2026  
**Author:** Business Analysis Team  
**Status:** Ready for Review & Approval  
**Change Log:**
- v1.1: Added Data Validation Enhancement section (11.1-11.10)
