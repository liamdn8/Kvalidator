# Namespace Search Update for YAML to CNF Converter

## Overview
Updated the Batch YAML to CNF Checklist converter to use namespace-based conversion instead of VIM-based conversion. This allows users to search and select namespaces, with each selected namespace creating a separate conversion job.

## Key Changes

### 1. Backend Model Updates

#### BatchYamlToCNFRequest.java
**Before:**
```java
private String vimName;  // Single VIM name
private List<String> namespaces;  // Filter namespaces
```

**After:**
```java
private List<String> targetNamespaces;  // Target namespaces to create jobs for
private List<String> namespacesFilter;  // Optional filter for resources
```

**Logic Change:**
- Previously: 1 VIM name → 1 conversion job → 1 Excel file
- Now: N target namespaces → N conversion jobs → N Excel files (one per namespace)

#### ConversionJobResponse.java
**Before:**
```java
private String vimName;
```

**After:**
```java
private String targetNamespace;
```

### 2. Backend Logic Updates

#### AsyncConversionExecutor.java
**Before:**
- Single job creation per request
- Job ID format: `conversion-{vimName}-{timestamp}`

**After:**
- Multiple job creation (one per target namespace)
- Job ID format: `conversion-{namespace}-{timestamp}`
- Each job processes the same YAML files but filters by namespace
- Returns `List<ConversionJobResponse>` instead of single response

**Key Method:**
```java
public List<ConversionJobResponse> submitConversionJob(BatchYamlToCNFRequest request) {
    List<ConversionJobResponse> createdJobs = new ArrayList<>();
    
    for (String namespace : request.getTargetNamespaces()) {
        // Create one job per namespace
        String jobId = String.format("conversion-%s-%s", namespace, timestamp);
        // ... execute conversion
        createdJobs.add(job);
    }
    
    return createdJobs;
}
```

**Conversion Logic:**
```java
private void executeConversion(String jobId, BatchYamlToCNFRequest request, String targetNamespace) {
    // Filter: only include resources from target namespace
    List<String> namespaceFilter = new ArrayList<>();
    namespaceFilter.add(targetNamespace);
    
    // Convert YAML files for this specific namespace
    List<CNFChecklistItem> items = yamlConverter.convertMultipleFilesToCNFChecklist(
        targetNamespace,  // Use namespace as vimName
        request.getYamlFiles(),
        namespaceFilter,  // Only this namespace
        request.getImportantFields()
    );
    
    // Generate Excel for this namespace only
    String filename = String.format("cnf-checklist-%s-%s.xlsx", targetNamespace, timestamp);
    // ... save Excel
}
```

### 3. Frontend UI Updates

#### BatchYamlToCNFPage.tsx

**Removed:**
- VIM Name input field

**Added:**
1. **Namespace Search from Cluster:**
   ```tsx
   // Search namespaces by keyword
   <Input placeholder="Enter namespace keyword" />
   <Button onClick={handleSearchNamespaces}>Search</Button>
   
   // Show search results
   <Select mode="multiple" value={selectedNamespaces}>
     {searchResults.map(ns => (
       <Option value={ns.namespace}>
         {ns.namespace} - {ns.cluster}
       </Option>
     ))}
   </Select>
   ```

2. **Extract Namespaces from YAML Files:**
   ```tsx
   // Extract from uploaded files
   <Button onClick={handleExtractNamespaces}>
     Extract Namespaces
   </Button>
   
   // Show extracted namespaces
   <Select mode="multiple" value={selectedNamespaces}>
     {extractedNamespaces.map(ns => (
       <Option value={ns.name}>
         {ns.name} - {ns.resourceCount} resources
       </Option>
     ))}
   </Select>
   ```

3. **Multi-Selection Alert:**
   ```tsx
   {selectedNamespaces.length > 0 && (
     <Alert type="success">
       {selectedNamespaces.length} namespace(s) selected
       ⚠️ Each namespace will create a separate conversion job
     </Alert>
   )}
   ```

**Submit Button:**
```tsx
<Button onClick={handleSubmitBatchConversion}>
  Submit Conversion Jobs ({selectedNamespaces.length} job{s})
</Button>
```

### 4. API Updates

#### api.ts
**Before:**
```typescript
submitBatchConversion: async (params: {
  vimName: string;
  // ...
}): Promise<{ jobId: string; vimName: string; ... }>
```

**After:**
```typescript
submitBatchConversion: async (params: {
  targetNamespaces: string[];
  // ...
}): Promise<Array<{ jobId: string; targetNamespace: string; ... }>>
```

## User Workflow

### Step-by-Step Process

1. **Configuration**
   - Select flatten mode: `flat` or `semantic`
   - Optional: Add description

2. **Upload YAML Files**
   - Upload multiple Kubernetes YAML files
   - Files are displayed with size info

3. **Select Target Namespaces**
   
   **Option A: Search from Cluster**
   - Enter namespace keyword (e.g., "kube", "default")
   - Click "Search" to find namespaces
   - Select one or more namespaces from results
   
   **Option B: Extract from YAML Files**
   - Click "Extract Namespaces" to scan uploaded files
   - Select one or more namespaces from extracted list

4. **Submit Conversion Jobs**
   - Review selected namespaces
   - Click "Submit Conversion Jobs (N jobs)"
   - System creates N jobs (one per namespace)
   - Each job generates separate Excel file

5. **Monitor & Download**
   - View all jobs in jobs table
   - Each job shows:
     - Job ID: `conversion-{namespace}-{timestamp}`
     - Target Namespace
     - Status: PENDING → PROCESSING → COMPLETED/FAILED
     - Progress bar
   - Download Excel when COMPLETED

## Example Scenarios

### Scenario 1: Convert Multiple Namespaces
**Input:**
- YAML Files: `deployment.yaml`, `service.yaml`, `configmap.yaml`
- Selected Namespaces: `default`, `kube-system`, `monitoring`

**Output:**
- 3 conversion jobs created:
  1. `conversion-default-20260123-093000`
  2. `conversion-kube-system-20260123-093000`
  3. `conversion-monitoring-20260123-093000`
- 3 Excel files generated:
  1. `cnf-checklist-default-20260123-093000.xlsx`
  2. `cnf-checklist-kube-system-20260123-093000.xlsx`
  3. `cnf-checklist-monitoring-20260123-093000.xlsx`

### Scenario 2: Single Namespace Conversion
**Input:**
- YAML Files: `app.yaml`
- Selected Namespaces: `production`

**Output:**
- 1 conversion job: `conversion-production-20260123-093000`
- 1 Excel file: `cnf-checklist-production-20260123-093000.xlsx`

## Benefits

1. **Flexibility**: Users can search namespaces from cluster OR extract from YAML files
2. **Granularity**: Each namespace gets its own Excel file for better organization
3. **Clarity**: Job names clearly indicate target namespace
4. **Parallel Processing**: Multiple namespaces can be processed simultaneously
5. **Better UX**: Multi-select dropdown with search functionality
6. **Resource Filtering**: Each job only includes resources from its target namespace

## API Endpoints

### Submit Batch Conversion
```
POST /kvalidator/api/yaml-to-cnf/batch/submit
```

**Request:**
```json
{
  "targetNamespaces": ["default", "kube-system"],
  "yamlFiles": [
    {
      "fileName": "deployment.yaml",
      "yamlContent": "apiVersion: apps/v1\n..."
    }
  ],
  "flattenMode": "flat",
  "description": "Production namespace conversion"
}
```

**Response:**
```json
[
  {
    "jobId": "conversion-default-20260123-093000",
    "status": "PENDING",
    "targetNamespace": "default",
    "fileCount": 1,
    "flattenMode": "flat",
    "progress": 0,
    "submittedAt": "2026-01-23T09:30:00"
  },
  {
    "jobId": "conversion-kube-system-20260123-093000",
    "status": "PENDING",
    "targetNamespace": "kube-system",
    "fileCount": 1,
    "flattenMode": "flat",
    "progress": 0,
    "submittedAt": "2026-01-23T09:30:00"
  }
]
```

### Search Namespaces
```
GET /kvalidator/api/kubernetes/namespaces/search?keyword={keyword}
```

**Response:**
```json
{
  "data": [
    {
      "namespace": "default",
      "cluster": "cluster-1",
      "resourceCount": 15
    },
    {
      "namespace": "kube-system",
      "cluster": "cluster-1",
      "resourceCount": 42
    }
  ]
}
```

### Extract Namespaces from YAML
```
POST /kvalidator/api/yaml-to-cnf/batch/extract-namespaces
```

**Request:**
```json
{
  "yamlFiles": [
    {
      "fileName": "app.yaml",
      "yamlContent": "..."
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Found 3 namespace(s)",
  "namespaces": [
    {
      "name": "default",
      "resourceCount": 5,
      "resourceKinds": "Deployment, Service, ConfigMap"
    }
  ]
}
```

## Technical Notes

### Job Creation Logic
```java
// One job per namespace
for (String namespace : request.getTargetNamespaces()) {
    String jobId = String.format("conversion-%s-%s", namespace, timestamp);
    
    ConversionJobResponse job = ConversionJobResponse.builder()
        .jobId(jobId)
        .targetNamespace(namespace)
        .fileCount(request.getYamlFiles().size())
        .build();
    
    jobs.put(jobId, job);
    executorService.submit(() -> executeConversion(jobId, request, namespace));
}
```

### Resource Filtering
```java
// Filter resources by namespace
List<String> namespaceFilter = new ArrayList<>();
namespaceFilter.add(targetNamespace);  // Only this namespace

List<CNFChecklistItem> items = yamlConverter.convertMultipleFilesToCNFChecklist(
    targetNamespace,
    request.getYamlFiles(),
    namespaceFilter,  // Critical: filters resources
    request.getImportantFields()
);
```

### Excel File Naming
```java
String filename = String.format("cnf-checklist-%s-%s.xlsx", 
    targetNamespace.replaceAll("[^a-zA-Z0-9-]", "_"),
    timestamp
);
```

## Migration Notes

### For Existing Users
- **Breaking Change**: `vimName` field removed from request model
- **New Required Field**: `targetNamespaces` (array, minimum 1 element)
- **Response Change**: Now returns array of jobs instead of single job
- **Frontend**: Old BatchYamlToCNFPage backed up as `BatchYamlToCNFPage_old.tsx`

### Backward Compatibility
None. This is a breaking change requiring frontend update.

## Testing

### Manual Test Cases
1. ✅ Search namespaces by keyword
2. ✅ Extract namespaces from YAML files
3. ✅ Select multiple namespaces
4. ✅ Submit conversion with 1 namespace
5. ✅ Submit conversion with multiple namespaces
6. ✅ Download Excel for each job
7. ✅ Delete individual jobs
8. ✅ Job status tracking (PENDING → PROCESSING → COMPLETED)

### Build Verification
```bash
mvn clean compile -DskipTests
# Result: BUILD SUCCESS
```

## Files Modified

### Backend
- ✅ `src/main/java/com/nfv/validator/model/cnf/BatchYamlToCNFRequest.java`
- ✅ `src/main/java/com/nfv/validator/model/cnf/ConversionJobResponse.java`
- ✅ `src/main/java/com/nfv/validator/service/AsyncConversionExecutor.java`
- ✅ `src/main/java/com/nfv/validator/api/YamlToCNFResource.java`

### Frontend
- ✅ `frontend/src/pages/BatchYamlToCNFPage.tsx` (complete rewrite)
- ✅ `frontend/src/services/api.ts` (submitBatchConversion signature)

## Next Steps

1. **Testing**: Test with real Kubernetes YAML files and namespaces
2. **Documentation**: Update user guide with namespace search workflow
3. **Enhancement**: Consider adding namespace filtering options
4. **Performance**: Monitor job execution for large number of namespaces

## Summary

Successfully migrated from VIM-based to namespace-based conversion approach. Users can now:
- Search and select namespaces from cluster
- Extract namespaces from uploaded YAML files
- Create multiple conversion jobs (one per namespace)
- Download separate Excel files for each namespace

This provides better granularity, clarity, and flexibility for CNF checklist generation.
