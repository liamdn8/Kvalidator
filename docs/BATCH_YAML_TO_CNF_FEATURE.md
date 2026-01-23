# Batch YAML to CNF Checklist Converter - Feature Documentation

## Tổng quan

Nâng cấp tính năng **YAML to CNF Checklist Converter** để hỗ trợ:
- ✅ **Multiple YAML files** - Upload và xử lý nhiều file cùng lúc
- ✅ **Batch conversion jobs** - Async processing với job tracking
- ✅ **Smart namespace search** - Extract và filter namespaces từ nhiều files
- ✅ **Flatten mode selection** - Chọn giữa flat (standard) và semantic (v2)
- ✅ **Job management** - View, download, delete conversion jobs

## Workflow Comparison

### Single File (Trước)
```
Upload 1 YAML → Extract namespaces → Select → Generate → Download Excel
(Sync, immediate download)
```

### Batch Files (Mới)
```
Upload nhiều YAMLs → Extract namespaces → Select → Submit Job
                                                      ↓
                                           Background processing
                                                      ↓
                                            View jobs table
                                                      ↓
                                      Download Excel khi completed
```

## Kiến trúc

### Backend Components

#### 1. New Models

**YamlFileEntry.java**
```java
{
  "fileName": "deployment.yaml",
  "yamlContent": "...",
  "description": "Web deployment"
}
```

**BatchYamlToCNFRequest.java**
```java
{
  "vimName": "vim-hanoi",
  "yamlFiles": [
    { "fileName": "...", "yamlContent": "..." },
    { "fileName": "...", "yamlContent": "..." }
  ],
  "namespaces": ["production", "staging"],
  "flattenMode": "flat",  // or "semantic"
  "importantFields": [...],
  "description": "Production deployment checklist"
}
```

**ConversionJobResponse.java**
```java
{
  "jobId": "conversion-vim-hanoi-20260123-143022",
  "status": "PROCESSING",  // PENDING, PROCESSING, COMPLETED, FAILED
  "vimName": "vim-hanoi",
  "fileCount": 5,
  "namespaceCount": 3,
  "namespaces": ["production", "staging", "default"],
  "flattenMode": "flat",
  "totalItems": 125,
  "excelFilePath": "/tmp/.kvalidator/conversion-results/...",
  "progress": 75,
  "submittedAt": "2026-01-23T14:30:22",
  "completedAt": "2026-01-23T14:30:45"
}
```

#### 2. Updated Services

**YamlToCNFChecklistConverter.java**
- `extractNamespacesFromMultipleFiles(List<YamlFileEntry>)` - Extract từ nhiều files
- `convertMultipleFilesToCNFChecklist(...)` - Convert batch files

**AsyncConversionExecutor.java** (NEW)
- `submitConversionJob(BatchYamlToCNFRequest)` - Submit async job
- `getJobStatus(String jobId)` - Get job status
- `getAllJobs()` - List all jobs
- `downloadExcelFile(String jobId)` - Download result
- `deleteJob(String jobId)` - Clean up job

Features:
- In-memory job storage (có thể upgrade lên database)
- Thread pool với 5 workers
- Auto-save Excel files
- Progress tracking (0-100%)

#### 3. API Endpoints

**YamlToCNFResource.java** - `/kvalidator/api/yaml-to-cnf`

##### Batch Conversion
```
POST /batch/submit
Content-Type: application/json

Request:
{
  "vimName": "vim-hanoi",
  "yamlFiles": [
    {
      "fileName": "deployment.yaml",
      "yamlContent": "apiVersion: apps/v1\n...",
      "description": "Web deployment"
    },
    {
      "fileName": "service.yaml",
      "yamlContent": "apiVersion: v1\n..."
    }
  ],
  "namespaces": ["production"],
  "flattenMode": "flat",
  "description": "Production checklist"
}

Response:
{
  "jobId": "conversion-vim-hanoi-20260123-143022",
  "status": "PENDING",
  "vimName": "vim-hanoi",
  "fileCount": 2,
  "namespaces": ["production"],
  "flattenMode": "flat",
  "submittedAt": "2026-01-23T14:30:22"
}
```

##### Get Job Status
```
GET /batch/jobs/{jobId}

Response:
{
  "jobId": "conversion-vim-hanoi-20260123-143022",
  "status": "COMPLETED",
  "progress": 100,
  "totalItems": 125,
  "excelFilePath": "/tmp/.kvalidator/conversion-results/cnf-checklist-vim-hanoi-20260123-143022.xlsx"
}
```

##### Download Excel
```
GET /batch/jobs/{jobId}/download

Response: Excel file (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
```

##### Extract Namespaces (Batch)
```
POST /batch/extract-namespaces
Content-Type: application/json

Request:
{
  "yamlFiles": [
    {
      "fileName": "deployment.yaml",
      "yamlContent": "..."
    }
  ]
}

Response:
{
  "success": true,
  "message": "Found 3 namespace(s) across 2 YAML files",
  "namespaces": [
    {
      "name": "production",
      "resourceCount": 10,
      "resourceKinds": "Deployment, Service, ConfigMap"
    }
  ]
}
```

##### Get All Jobs
```
GET /batch/jobs

Response: Array of ConversionJobResponse
```

##### Delete Job
```
DELETE /batch/jobs/{jobId}

Response:
{
  "success": true,
  "message": "Job deleted successfully"
}
```

### Frontend Components

#### 1. API Service Updates

**frontend/src/services/api.ts**

New methods:
- `extractNamespacesFromBatch(yamlFiles)` - Extract từ nhiều files
- `submitBatchConversion(params)` - Submit batch job
- `getConversionJobStatus(jobId)` - Get job status
- `downloadConversionJobExcel(jobId)` - Download Excel
- `getAllConversionJobs()` - List jobs
- `deleteConversionJob(jobId)` - Delete job

#### 2. New Page

**frontend/src/pages/BatchYamlToCNFPage.tsx**

**Features:**
- Multiple file upload with list
- Namespace extraction from all files
- Smart namespace search và filter
- Flatten mode selector (Flat/Semantic)
- Job description input
- Submit batch job
- Jobs table với real-time polling
- Job details modal
- Download/Delete actions

**UI Layout:**

```
┌─────────────────────────────────────────────┐
│ Step 1: Configuration                       │
│   ├─ VIM Name                               │
│   ├─ Flatten Mode (Flat/Semantic)           │
│   └─ Description                            │
├─────────────────────────────────────────────┤
│ Step 2: Upload YAML Files                   │
│   ├─ Add YAML File(s) button                │
│   └─ Files List (with remove)               │
├─────────────────────────────────────────────┤
│ Step 3: Select Namespaces                   │
│   ├─ Extract Namespaces button              │
│   ├─ Multi-select dropdown                  │
│   └─ Namespaces table                       │
├─────────────────────────────────────────────┤
│ Step 4: Submit                               │
│   └─ Submit Conversion Job button           │
├─────────────────────────────────────────────┤
│ Conversion Jobs Table                        │
│   ├─ Job ID                                  │
│   ├─ Status (tag with color)                │
│   ├─ VIM Name                                │
│   ├─ Files count                             │
│   ├─ Items count                             │
│   ├─ Progress bar                            │
│   ├─ Flatten mode                            │
│   └─ Actions (View/Download/Delete)         │
└─────────────────────────────────────────────┘
```

**Job States:**
- 🔵 PENDING - Waiting to process
- 🟠 PROCESSING - Converting files (with progress %)
- 🟢 COMPLETED - Ready to download
- 🔴 FAILED - Error occurred

**Polling:**
- Auto-refresh jobs table every 3 seconds
- Shows real-time progress updates

#### 3. Routing Updates

**Menu Structure:**
```
YAML to CNF
  ├─ Single File (original)
  └─ Batch Files (new)
```

## Flatten Modes

### Flat Mode (Standard)
- Traditional flattening algorithm
- Simpler field paths
- Good for simple structures
- Uses `YamlDataCollector`

Example:
```
spec.replicas = 3
spec.template.spec.containers[0].image = nginx:1.21
```

### Semantic Mode (V2)
- Preserves nested structures
- Better for complex objects
- More accurate for arrays and objects
- Uses `YamlDataCollectorV2`

Example:
```
spec.replicas = 3
spec.template.spec.containers[0].image = nginx:1.21
spec.template.spec.containers[0].env[0].name = LOG_LEVEL
```

## Usage Examples

### Example 1: Basic Batch Conversion

**Input: 3 YAML files**
1. `web-deployment.yaml` - Web application deployment
2. `api-deployment.yaml` - API server deployment
3. `config.yaml` - ConfigMaps và Services

**Steps:**
1. Enter VIM Name: "vim-production"
2. Select Flatten Mode: "Flat"
3. Upload 3 YAML files
4. Click "Extract Namespaces"
   - Found: production, staging, default
5. Select namespaces: production, staging
6. Add description: "Production Q1 2026 deployment"
7. Click "Submit Conversion Job"
8. Wait for job to complete
9. Download Excel file

**Output:**
- Excel với ~150 checklist items
- Từ 2 namespaces (production, staging)
- 3 files processed

### Example 2: Semantic Mode Conversion

**Input:**
- Complex Kubernetes resources with nested arrays
- StatefulSets với volumeClaimTemplates
- ConfigMaps với nhiều data fields

**Configuration:**
- VIM Name: "vim-staging"
- Flatten Mode: "Semantic"
- Namespaces: All

**Result:**
- Preserves nested structure
- Better handling của complex objects
- More accurate field paths

## Job Management

### View Job Details
Click "View" button trong jobs table để xem:
- Job ID
- Status với color tag
- VIM Name
- Number of files
- Total items generated
- Flatten mode
- Progress (nếu đang processing)
- Submission time
- Completion time
- Error message (nếu failed)

### Download Excel
- Chỉ available khi job status = COMPLETED
- Click "Download" button
- File auto-download với tên: `{jobId}.xlsx`

### Delete Job
- Remove job từ list
- Delete Excel file từ server
- Clean up resources

## Error Handling

### Backend Validation
- At least 1 YAML file required
- VIM name required
- Valid YAML format
- Valid flatten mode (flat/semantic)
- Each file must have content

### Job Failures
Causes:
- Invalid YAML syntax
- No valid Kubernetes resources
- File read errors
- Excel generation errors

Result:
- Job status = FAILED
- Error message displayed
- Can view error details
- Can delete failed job

### Frontend Errors
- Network errors
- Invalid input
- Job not found
- Download errors
- User-friendly messages

## API Testing

### Test Batch Submit
```bash
curl -X POST http://localhost:8080/kvalidator/api/yaml-to-cnf/batch/submit \
  -H "Content-Type: application/json" \
  -d '{
    "vimName": "vim-test",
    "yamlFiles": [
      {
        "fileName": "deployment.yaml",
        "yamlContent": "apiVersion: apps/v1\nkind: Deployment\n..."
      }
    ],
    "namespaces": ["production"],
    "flattenMode": "flat"
  }'
```

### Test Get Job Status
```bash
curl http://localhost:8080/kvalidator/api/yaml-to-cnf/batch/jobs/{jobId}
```

### Test Download
```bash
curl -O http://localhost:8080/kvalidator/api/yaml-to-cnf/batch/jobs/{jobId}/download
```

## Files Created/Modified

### Backend (7 files)
```
✅ NEW  src/main/java/com/nfv/validator/model/cnf/YamlFileEntry.java
✅ NEW  src/main/java/com/nfv/validator/model/cnf/BatchYamlToCNFRequest.java
✅ NEW  src/main/java/com/nfv/validator/model/cnf/ConversionJobResponse.java
✅ NEW  src/main/java/com/nfv/validator/service/AsyncConversionExecutor.java
✅ MOD  src/main/java/com/nfv/validator/service/YamlToCNFChecklistConverter.java
✅ MOD  src/main/java/com/nfv/validator/api/YamlToCNFResource.java
```

### Frontend (4 files)
```
✅ NEW  frontend/src/pages/BatchYamlToCNFPage.tsx
✅ MOD  frontend/src/services/api.ts
✅ MOD  frontend/src/App.tsx
✅ MOD  frontend/src/layouts/MainLayout.tsx
```

### Documentation (1 file)
```
✅ NEW  docs/BATCH_YAML_TO_CNF_FEATURE.md
```

## Benefits

1. **Scalability**: Xử lý nhiều files cùng lúc
2. **Async Processing**: Không block UI, background jobs
3. **Job Tracking**: Real-time progress monitoring
4. **Flexibility**: Chọn flatten mode phù hợp
5. **Smart Search**: Namespace extraction từ nhiều files
6. **User Experience**: Jobs table với polling, download management

## Performance

### Single File Mode
- Sync processing
- Immediate download
- Good for: 1-2 files, quick testing

### Batch Mode
- Async processing
- Job tracking
- Good for: 3+ files, production use

**Processing Time:**
- ~1-2 seconds per file
- ~5-10 files: 10-20 seconds
- Progress updates every second

## Limitations

### Current Implementation
- In-memory job storage (không persist qua restart)
- Max 5 concurrent jobs (thread pool limit)
- Files stored in `/tmp` (có thể bị xóa)

### Future Enhancements
- Database persistence cho jobs
- Redis/Queue system cho scalability
- S3/Object storage cho Excel files
- Job scheduling/retry mechanism
- Email notification khi job complete

## Migration Guide

### Từ Single File → Batch

**Before (Single):**
```typescript
const blob = await validationApi.convertYamlToExcel({
  vimName: 'vim-hanoi',
  yamlContent: yaml1,
  namespaces: ['production']
});
// Immediate download
```

**After (Batch):**
```typescript
const job = await validationApi.submitBatchConversion({
  vimName: 'vim-hanoi',
  yamlFiles: [
    { fileName: 'f1.yaml', yamlContent: yaml1 },
    { fileName: 'f2.yaml', yamlContent: yaml2 }
  ],
  namespaces: ['production'],
  flattenMode: 'flat'
});

// Poll status
const status = await validationApi.getConversionJobStatus(job.jobId);

// Download when complete
if (status.status === 'COMPLETED') {
  const blob = await validationApi.downloadConversionJobExcel(job.jobId);
}
```

## Troubleshooting

### Jobs stuck in PENDING
- Check thread pool availability
- Check server logs
- Restart server if needed

### Excel file not found
- Job may have been deleted
- File cleanup may have occurred
- Check `/tmp/.kvalidator/conversion-results/`

### Namespace extraction fails
- Verify YAML syntax
- Check if resources have metadata.namespace
- Try with valid Kubernetes YAML

## Conclusion

Batch YAML to CNF Converter nâng cấp đáng kể khả năng của hệ thống:
- Hỗ trợ enterprise use cases với nhiều files
- Async processing cho better UX
- Flexible flatten modes
- Professional job management

---

**Status**: ✅ READY FOR TESTING
**Version**: 2.0
**Date**: January 23, 2026
