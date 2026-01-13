# KValidator REST API

## Overview
KValidator now provides RESTful APIs built with Quarkus 2.16 to execute all validation features programmatically.

## Running the API Server

### Development Mode (with live reload)
```bash
mvn quarkus:dev
```

### Production Mode
```bash
# Build
mvn clean package

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

## API Endpoints

### Base URL
```
http://localhost:8080
```

### OpenAPI Documentation
- **Swagger UI**: http://localhost:8080/swagger-ui
- **OpenAPI Spec**: http://localhost:8080/openapi

### 1. Single Validation
**Endpoint**: `POST /api/validate`

Execute a single validation comparing namespaces against a baseline or with each other.

**Request Body**:
```json
{
  "baseline": "baseline-design.yaml",  // Optional: for baseline comparison
  "namespaces": [                       // Required: list of namespaces
    "kind-kind-infra-test/app-dev",
    "kind-kind-infra-test/app-staging"
  ],
  "kinds": ["Deployment", "Service"],   // Optional: filter by resource kinds
  "output": "validation-result.xlsx",   // Optional: Excel output filename
  "verbose": true,                      // Optional: include detailed results
  "config": "validation-config.yaml"    // Optional: validation config file
}
```

**Response**:
```json
{
  "status": "SUCCESS",
  "message": "Validation completed successfully",
  "reportPath": "results/validation-20260113-130000/validation-result.xlsx",
  "downloadUrl": "/api/reports/download?file=results/validation-20260113-130000/validation-result.xlsx",
  "totalObjects": 311,
  "differences": 155,
  "executionTimeSeconds": 8.32,
  "details": {                           // Only if verbose=true
    "kind-kind-infra-test/app-dev_vs_kind-kind-infra-test/app-staging": {
      "totalObjects": 311,
      "matchedObjects": 156,
      "differencesCount": 155,
      "onlyInLeft": 2,
      "onlyInRight": 3
    }
  }
}
```

### 2. Batch Validation
**Endpoint**: `POST /api/validate/batch`

Execute multiple validations in one request, either sequentially or in parallel.

**Request Body**:
```json
{
  "requests": [
    {
      "name": "Dev vs Staging",
      "namespaces": [
        "kind-kind-infra-test/app-dev",
        "kind-kind-infra-test/app-staging"
      ],
      "verbose": false
    },
    {
      "name": "Staging vs Production",
      "namespaces": [
        "kind-kind-infra-test/app-staging",
        "kind-kind-infra-test/app-prod"
      ]
    }
  ],
  "globalSettings": {
    "maxParallelRequests": 3,             // 0 for sequential, >0 for parallel
    "outputDirectory": "batch-results"    // Output directory name
  }
}
```

**Response**:
```json
{
  "status": "SUCCESS",
  "message": "Batch validation completed: 2 successful, 0 failed out of 2 total",
  "totalRequests": 2,
  "successfulRequests": 2,
  "failedRequests": 0,
  "batchSummaryReportPath": "results/batch-results-20260113-130000/batch-summary.xlsx",
  "batchSummaryDownloadUrl": "/api/reports/download?file=results/batch-results-20260113-130000/batch-summary.xlsx",
  "totalExecutionTimeSeconds": 16.5,
  "results": [
    {
      "requestName": "Dev vs Staging",
      "status": "SUCCESS",
      "reportPath": "results/batch-results-20260113-130000/dev-vs-staging.xlsx",
      "downloadUrl": "/api/reports/download?file=results/batch-results-20260113-130000/dev-vs-staging.xlsx",
      "objectsCompared": 311,
      "differences": 155,
      "executionTimeSeconds": 8.2
    }
  ]
}
```

### 3. Download Report
**Endpoint**: `GET /api/reports/download?file={filePath}`

Download a generated Excel report.

**Query Parameters**:
- `file`: Relative path to the Excel file (e.g., `results/validation-20260113-130000/report.xlsx`)

**Response**: Excel file download (Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)

### 4. List Reports
**Endpoint**: `GET /api/reports/list`

List all available Excel reports in the results directory.

**Response**:
```json
{
  "reports": [
    {
      "fileName": "validation-result.xlsx",
      "path": "results/validation-20260113-130000/validation-result.xlsx",
      "size": 12345,
      "downloadUrl": "/api/reports/download?file=results/validation-20260113-130000/validation-result.xlsx"
    }
  ]
}
```

### 5. Health Check
**Endpoint**: `GET /api/validate/health`

Check if the validation service is running.

**Response**:
```json
{
  "status": "UP",
  "message": "Validation service is running"
}
```

## Example: Using curl

### Single Validation
```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d @examples/api-request-single.json
```

### Batch Validation
```bash
curl -X POST http://localhost:8080/api/validate/batch \
  -H "Content-Type: application/json" \
  -d @examples/api-request-batch.json
```

### Download Report
```bash
curl -O -J "http://localhost:8080/api/reports/download?file=results/validation-20260113-130000/report.xlsx"
```

## Configuration

Application configuration is in `src/main/resources/application.properties`:

```properties
# HTTP port
quarkus.http.port=8080

# CORS (enabled for all origins)
quarkus.http.cors=true

# OpenAPI/Swagger
quarkus.swagger-ui.enable=true
quarkus.swagger-ui.path=/swagger-ui

# Logging
quarkus.log.level=INFO
quarkus.log.category."com.nfv.validator".level=DEBUG
```

## Features

✅ All CLI features available via REST API
✅ OpenAPI 3.0 specification
✅ Interactive Swagger UI documentation
✅ CORS enabled for web applications
✅ File download support for Excel reports
✅ Batch validation with parallel execution
✅ Health check endpoints
✅ Comprehensive error handling

## Architecture

The API is built using:
- **Quarkus 2.16.12**: Supersonic subatomic Java framework
- **RESTEasy Reactive**: High-performance reactive REST
- **SmallRye OpenAPI**: OpenAPI 3.0 specification & Swagger UI
- **Jackson**: JSON serialization
- **CDI (Arc)**: Dependency injection

All existing validation logic (KubernetesClusterManager, BatchExecutor, ExcelReportGenerator, etc.) is reused through service layers.
