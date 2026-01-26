# CNF Checklist Feature - Luồng Thực Thi Chi Tiết

## Tổng Quan

Tài liệu này mô tả chi tiết luồng thực thi của tính năng **CNF Checklist Validation** khi sử dụng qua Web UI, từ khi người dùng submit request cho đến khi hiển thị kết quả.

## Sơ Đồ Tổng Quan

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FRONTEND (React + TypeScript)                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. CNFChecklistPage.tsx                                               │
│     - Người dùng nhập checklist (JSON/Table/Excel)                     │
│     - Configure matching strategy (exact/value/identity)                │
│     - Configure ignore fields                                           │
│     - Click "Start Validation"                                          │
│                                                                         │
│  2. handleValidate() function                                           │
│     - Parse input data thành CNFChecklistRequest                        │
│     - Gọi validationApi.submitCNFChecklistValidation(request)          │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTP POST
                               │ /kvalidator/api/validate/cnf-checklist
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       BACKEND API (Quarkus + Java)                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  3. ValidationResource.submitCNFChecklistValidation()                   │
│     - Validate request (check items not empty)                          │
│     - request.validate() - validate từng item                           │
│     - autoConvertToIdentityMatching() - DISABLED (user phải tự config)  │
│     - CNFChecklistService.convertToBatchRequestFlattened()              │
│     - jobService.createBatchJob()                                       │
│     - executor.executeBatchAsync()                                      │
│     - Return 201 Created với jobId                                      │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER - CONVERSION                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  4. CNFChecklistService.convertToBatchRequestFlattened()                │
│     Input: CNFChecklistRequest                                          │
│     Output: BatchValidationRequest                                      │
│                                                                         │
│     4.1. convertToBaseline(cnfRequest)                                  │
│          - Group items by "vimName/namespace"                           │
│          - Tạo FlatNamespaceModel cho mỗi namespace                     │
│          - Tạo FlatObjectModel cho mỗi object                           │
│          - addFieldToObject() - add expected values vào metadata/spec   │
│          - Return Map<String, FlatNamespaceModel>                       │
│                                                                         │
│     4.2. Create BatchValidationRequest                                  │
│          - Không tạo YAML file nữa (dùng flattened approach)            │
│          - Set baseline models trực tiếp                                │
│          - Set cnfChecklistRequest reference                            │
│          - Set globalSettings (continueOnError, generateSummaryReport)  │
│          - extractNamespaces() - get danh sách vimName/namespace        │
│          - extractKinds() - get danh sách Kubernetes kinds              │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    ASYNC EXECUTOR - BACKGROUND THREAD                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  5. AsyncValidationExecutor.executeBatchAsync()                         │
│     - Submit task vào ExecutorService (background thread)               │
│     - Return ngay lập tức (non-blocking)                                │
│                                                                         │
│  6. Background Thread: executeValidation()                              │
│     - jobService.startJob(jobId) - update status = PROCESSING           │
│     - Detect if CNF Checklist: check cnfChecklistRequest != null        │
│     - Branch: executeCNFChecklistValidation()                           │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              CNF CHECKLIST VALIDATION - CORE LOGIC                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  7. executeCNFChecklistValidation(jobId, request, baselineMap)          │
│                                                                         │
│     7.1. Determine Semantic V2 Strategy                                 │
│          - Check CNFChecklistRequest.matchingStrategy                   │
│          - If "identity" → useSemanticV2 = true                         │
│          - If "exact" or "value" → useSemanticV2 = false                │
│                                                                         │
│     7.2. Load ValidationConfig                                          │
│          - Load validation-config.yaml (default ignore rules)           │
│          - Merge với ignoreFields từ CNFChecklistRequest                │
│          - Merge với ignoreFields từ job request                        │
│                                                                         │
│     7.3. Update Progress: "Preparing CNF checklist validation" (5%)     │
│                                                                         │
│     7.4. FOR EACH namespace in baselineMap:                             │
│          String namespaceKey = "vimName/namespace"                      │
│          FlatNamespaceModel baselineModel = entry.getValue()            │
│                                                                         │
│          7.4.1. Update Progress: "Validating {namespaceKey}" (20-80%)   │
│                                                                         │
│          7.4.2. Collect Actual Data from Kubernetes                     │
│                 - clusterManager.getClient(vimName)                     │
│                                                                         │
│                 IF useSemanticV2:                                       │
│                    - K8sDataCollectorV2.collectNamespace()              │
│                    - SemanticToFlatAdapter.toFlatModel()                │
│                 ELSE:                                                   │
│                    - K8sDataCollector.collectNamespace()                │
│                                                                         │
│          7.4.3. Filter Ignored Fields                                   │
│                 - validationConfig.filterIgnoredFields(baselineModel)   │
│                 - validationConfig.filterIgnoredFields(actualModel)     │
│                                                                         │
│          7.4.4. Compare Baseline vs Actual                              │
│                 String comparisonKey = buildComparisonKey(...)          │
│                 String baselineLabel = "vimName/namespace (Baseline)"   │
│                 String actualLabel = "vimName/namespace (Actual)"       │
│                                                                         │
│                 IF useSemanticV2:                                       │
│                    comparison = ValidationServiceV2.compareFlat(...)    │
│                 ELSE:                                                   │
│                    comparison = NamespaceComparator.compareNamespace(...) │
│                                                                         │
│                 comparisons.put(comparisonKey, comparison)              │
│                 totalDifferences += comparison.getSummary()...          │
│                                                                         │
│     7.5. Create Results Directory                                       │
│          Path resultsDir = jobService.createJobResultsDirectory(jobId)  │
│                                                                         │
│     7.6. Convert to CNF Format (80%)                                    │
│          List<CnfComparison> cnfComparisons =                           │
│              cnfChecklistService.convertToCnfComparison(...)            │
│                                                                         │
│     7.7. Generate CNF Checklist Excel Report (85%)                      │
│          CNFChecklistExcelGenerator.generateReport(...)                 │
│          → cnf-checklist-validation.xlsx                                │
│                                                                         │
│     7.8. Export JSON Results (95%)                                      │
│          - validation-results.json (standard format)                    │
│          - cnf-results.json (CNF-specific format for Web UI)            │
│                                                                         │
│     7.9. Complete Job (100%)                                            │
│          jobService.completeJob(jobId, resultsDir, ...)                 │
│          - Update status = COMPLETED                                    │
│          - Set resultsPath                                              │
│          - Set objectsValidated, differencesFound                       │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                 CNF COMPARISON CONVERSION                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  8. CNFChecklistService.convertToCnfComparison()                        │
│                                                                         │
│     Input: CNFChecklistRequest, Map<String, NamespaceComparison>        │
│     Output: List<CnfComparison>                                         │
│                                                                         │
│     8.1. Create Map: fieldKey → CNFChecklistItem                        │
│          (để biết field nào cần validate)                               │
│                                                                         │
│     8.2. Group comparisons by vimName/namespace                         │
│                                                                         │
│     8.3. FOR EACH vimName/namespace:                                    │
│          - Create CnfComparison                                         │
│          - Extract NamespaceComparison                                  │
│                                                                         │
│          8.3.1. FOR EACH objectComparison:                              │
│                 FOR EACH keyComparison (field difference):               │
│                     - Get fieldPath from keyComparison                   │
│                     - Find matching CNFChecklistItem                     │
│                     - IF found → create CnfChecklistResult:              │
│                       • vimName, namespace, kind, objectName             │
│                       • fieldKey, manoValue (expected)                   │
│                       • actualValue (from K8s)                           │
│                       • status (MATCH / MISMATCH / MISSING / IGNORED)   │
│                       • message (description)                            │
│                     - Add to CnfComparison.items                         │
│                                                                         │
│          8.3.2. Calculate summary stats:                                │
│                 - totalFields, totalMatches, totalDifferences            │
│                 - totalMissing, totalIgnored                             │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      FRONTEND - POLLING & RESULTS                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  9. CNFChecklistPage - Polling Job Status                               │
│     - validationApi.pollJobStatus(jobId, callback, 900, 2000)           │
│     - Poll mỗi 2 giây, tối đa 900 lần = 30 phút                         │
│     - Callback update UI: setJobStatus(job)                             │
│     - Show progress bar với job.progress                                │
│                                                                         │
│  10. Job COMPLETED                                                      │
│      - setCompletedJobId(jobId)                                         │
│      - Render: <CnfChecklistResults jobId={completedJobId} />           │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    RESULTS DISPLAY COMPONENT                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  11. CnfChecklistResults.tsx                                            │
│                                                                         │
│      11.1. useEffect - Fetch Results                                    │
│            - validationApi.getJobStatus(jobId)                          │
│            - validationApi.getBatchIndividualJobs(jobId)                │
│            - FOR EACH individualJobId:                                  │
│                validationApi.getCnfValidationResults(individualJobId)   │
│                                                                         │
│      11.2. If job still PROCESSING:                                     │
│            - Start polling (interval = 5 seconds)                       │
│            - Timeout after 30 minutes → show timeout modal              │
│                                                                         │
│      11.3. Display Results                                              │
│            - Overview statistics (total fields, matches, differences)   │
│            - Object-level stats (matched objects vs total)              │
│            - <CnfValidationResults result={cnfResult} />                │
│                                                                         │
│      11.4. Export Options                                               │
│            - Export to Excel (local generation)                         │
│            - Download server-generated Excel report                     │
│                                                                         │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                CNF VALIDATION RESULTS DISPLAY                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  12. CnfValidationResults.tsx                                           │
│                                                                         │
│      12.1. mapCnfToValidationResult()                                   │
│            - Convert CnfValidationResultJson → ValidationResultJson     │
│            - Để tái sử dụng ValidationResults component                 │
│                                                                         │
│      12.2. Render Results per Namespace                                 │
│            FOR EACH nsResult in results:                                │
│              - Show VIM/Namespace header                                │
│              - Group items by object (kind/name)                        │
│              - Show table with columns:                                 │
│                • Object (kind/name)                                     │
│                • Field Key                                              │
│                • Expected Value (MANO)                                  │
│                • Actual Value (K8s)                                     │
│                • Status (✓ MATCH / ✗ MISMATCH / ? MISSING / ⊘ IGNORED) │
│                                                                         │
│      12.3. Summary Statistics                                           │
│            - Total Fields, Matches, Differences                         │
│            - Pass/Fail per namespace                                    │
│            - Color-coded badges                                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Chi Tiết Các Bước Chính

### 1. Frontend Input Processing

**File:** `frontend/src/pages/CNFChecklistPage.tsx`

**Chức năng:**
- Hỗ trợ 3 input modes:
  - **JSON Mode**: Paste JSON array trực tiếp
  - **Table Mode**: Nhập từng item qua form
  - **Excel Upload**: Upload Excel file với checklist

**Validation:**
```typescript
// Mỗi item cần có đầy đủ fields:
{
  vimName: string;      // Cluster/VIM name
  namespace: string;    // K8s namespace
  kind: string;         // Resource type
  objectName: string;   // Object name
  fieldKey: string;     // Field path (e.g., "spec.replicas")
  manoValue: string;    // Expected value
}
```

**Configuration:**
- `matchingStrategy`: "exact" | "value" | "identity"
- `ignoreFields`: Array of field paths to exclude

### 2. API Request

**Endpoint:** `POST /kvalidator/api/validate/cnf-checklist`

**Request Body:**
```json
{
  "items": [...],
  "description": "CNF Checklist Validation",
  "matchingStrategy": "value",
  "ignoreFields": ["metadata.namespace", "status"]
}
```

**Response:**
```json
{
  "jobId": "cnf-checklist-20260126-143022",
  "status": "PENDING",
  "submittedAt": "2026-01-26T14:30:22Z",
  "progress": 0,
  "message": "Job submitted"
}
```

### 3. Backend Validation & Conversion

**File:** `ValidationResource.java`

**Validation Steps:**
1. Check items not empty
2. Validate each item structure
3. Auto-convert to identity matching (DISABLED - user must manually configure)

**Conversion to Batch Request:**
- Group items by `vimName/namespace`
- Create baseline namespace models
- Each namespace = 1 validation request in batch

### 4. Baseline Model Creation

**File:** `CNFChecklistService.java` → `convertToBaseline()`

**Process:**
```
Input: CNFChecklistRequest
  items = [
    {vimName: "vim-hanoi", namespace: "default", kind: "Deployment", 
     objectName: "web-app", fieldKey: "spec.replicas", manoValue: "3"},
    {vimName: "vim-hanoi", namespace: "default", kind: "Deployment",
     objectName: "web-app", fieldKey: "spec.template.spec.containers[0].image", 
     manoValue: "nginx:1.19"}
  ]

Step 1: Group by vimName/namespace
  "vim-hanoi/default" → [item1, item2]

Step 2: For each namespace group
  Create FlatNamespaceModel {
    name: "default",
    clusterName: "vim-hanoi",
    objects: Map<objectName, FlatObjectModel>
  }

Step 3: For each object (group by objectName)
  Create FlatObjectModel {
    kind: "Deployment",
    name: "web-app",
    namespace: "default",
    metadata: {},
    spec: {
      "replicas": "3",
      "template.spec.containers[0].image": "nginx:1.19"
    }
  }

Output: Map<"vim-hanoi/default", FlatNamespaceModel>
```

### 5. Async Execution

**File:** `AsyncValidationExecutor.java`

**Thread Pool:**
- ExecutorService với background threads
- Non-blocking API response
- Job status tracking

**Progress Updates:**
```
5%   - Preparing CNF checklist validation
20%  - Validating vim-hanoi/default
50%  - Validating vim-hanoi/production
80%  - Converting to CNF format
85%  - Generating CNF Excel report
95%  - Exporting JSON results
100% - Completed
```

### 6. Data Collection from Kubernetes

**K8s Data Collectors:**

**V1 (Flat Collector):**
- `K8sDataCollector.collectNamespace()`
- Collect tất cả resources trong namespace
- Convert thành FlatObjectModel
- Simple field matching

**V2 (Semantic Collector):**
- `K8sDataCollectorV2.collectNamespace()`
- Semantic array matching
- Identity-based comparison
- Support complex nested structures

**Khi nào dùng V2:**
- `matchingStrategy = "identity"`
- Có array fields với identity keys (e.g., `containers[name=nginx]`)

### 7. Comparison Logic

**Standard Comparison (V1):**
```java
NamespaceComparator.compareNamespace(
  baselineObjects,  // Expected values từ CNF checklist
  actualObjects,    // Runtime values từ K8s
  baselineLabel,    // "vim-hanoi/default (Baseline)"
  actualLabel,      // "vim-hanoi/default (Actual)"
  validationConfig  // Ignore rules
)
```

**Semantic Comparison (V2):**
```java
ValidationServiceV2.compareFlat(
  baselineModel,
  actualModel,
  baselineLabel,
  actualLabel,
  validationConfig
)
```

**Output:**
```java
NamespaceComparison {
  summary: {
    totalObjects: 10,
    matchedObjects: 8,
    differencesCount: 5,
    ...
  },
  objectComparisons: [
    {
      objectName: "web-app",
      kind: "Deployment",
      status: "DIFFERENT",
      keyComparisons: [
        {
          key: "spec.replicas",
          status: "DIFFERENT",
          leftValue: "3",
          rightValue: "5"
        }
      ]
    }
  ]
}
```

### 8. CNF Format Conversion

**File:** `CNFChecklistService.java` → `convertToCnfComparison()`

**Purpose:** Convert NamespaceComparison → CnfComparison
- Filter để chỉ show fields trong checklist
- Format theo CNF checklist structure
- Tính toán summary statistics

**Output:**
```java
CnfComparison {
  vimName: "vim-hanoi",
  namespace: "default",
  items: [
    {
      kind: "Deployment",
      objectName: "web-app",
      fieldKey: "spec.replicas",
      manoValue: "3",
      actualValue: "5",
      status: "MISMATCH",
      message: "Expected: 3, Actual: 5"
    }
  ],
  summary: {
    totalFields: 10,
    totalMatches: 8,
    totalDifferences: 2,
    totalMissing: 0,
    totalIgnored: 0
  }
}
```

### 9. Report Generation

**Excel Report:**
- File: `cnf-checklist-validation.xlsx`
- Generator: `CNFChecklistExcelGenerator.java`
- Sheets:
  - Summary: Overall statistics
  - Per Namespace: Detailed results
  - Mismatches: Only failed validations

**JSON Reports:**
- `validation-results.json`: Standard format (for Excel export)
- `cnf-results.json`: CNF-specific format (for Web UI)

### 10. Frontend Results Display

**Component:** `CnfChecklistResults.tsx`

**Features:**
- Auto-polling khi job chưa complete
- Timeout after 30 minutes
- Statistics overview
- Tabbed interface per namespace
- Export to Excel (client-side)
- Download server Excel report

**Result Visualization:**
- Color-coded status badges
- Expandable object groups
- Filterable/sortable tables
- Field-level comparison details

## Các Điểm Quan Trọng

### 1. Matching Strategy

**exact:**
- So sánh chính xác cả type và value
- `"3"` ≠ `3` (string vs number)

**value:**
- So sánh value, ignore type
- `"3"` == `3`
- DEFAULT strategy

**identity:**
- Dùng semantic V2 comparison
- Support array identity matching
- Example: `containers[name=nginx].image`

### 2. Ignore Fields

**Global Config:** `validation-config.yaml`
```yaml
ignoreFields:
  - metadata.creationTimestamp
  - metadata.generation
  - metadata.resourceVersion
  - status
```

**Custom từ Request:**
```json
{
  "items": [...],
  "ignoreFields": ["metadata.annotations", "spec.clusterIP"]
}
```

**Merge Logic:**
- Load default config
- Merge với CNF request ignoreFields
- Merge với job request ignoreFields

### 3. Error Handling

**Validation Errors:**
- Empty items array → 400 Bad Request
- Missing required fields → 400 Bad Request
- Invalid field path → Log warning, continue

**Runtime Errors:**
- K8s connection failed → Mark job as FAILED
- Object not found → Status = MISSING
- Comparison error → Log error, continue with next object

### 4. Performance Considerations

**Parallelization:**
- Mỗi namespace được validate tuần tự (trong 1 job)
- Multiple jobs có thể chạy parallel (via ExecutorService)

**Timeout:**
- Frontend polling: 30 minutes max
- Backend: No hard timeout (depends on K8s response time)

**Memory:**
- Flattened approach (không tạo YAML files)
- Stream processing cho large results

## Troubleshooting Guide

### Issue 1: Job bị stuck ở PROCESSING

**Nguyên nhân có thể:**
- K8s cluster không accessible
- Namespace không tồn tại
- Network timeout

**Cách check:**
- Xem logs backend: `logs/kvalidator.log`
- Check job status qua API: `GET /validate/{jobId}`
- Verify K8s connection: kubectl get ns

### Issue 2: Results không đúng

**Nguyên nhân có thể:**
- Matching strategy không phù hợp
- Ignore fields chưa đủ
- Field path sai format

**Cách fix:**
- Thử với `matchingStrategy: "exact"`
- Review ignore fields config
- Validate field path syntax

### Issue 3: Frontend timeout

**Nguyên nhân:**
- Validation quá lâu (>30 phút)
- Network issue

**Cách fix:**
- Giảm số items trong checklist
- Split thành multiple smaller jobs
- Check backend logs

### Issue 4: Excel report missing

**Nguyên nhân:**
- Job chưa COMPLETED
- File generation failed
- Permissions issue

**Cách check:**
- Check job status
- Verify resultsPath trong job response
- Check file system permissions

## Best Practices

1. **Checklist Design:**
   - Group items by namespace để dễ quản lý
   - Sử dụng descriptive field keys
   - Test với small checklist trước

2. **Matching Strategy:**
   - Mặc định dùng "value" cho simple comparisons
   - Dùng "identity" cho array matching
   - Dùng "exact" khi cần strict type checking

3. **Ignore Fields:**
   - Bắt đầu với default config
   - Chỉ add custom rules khi cần thiết
   - Document tại sao ignore mỗi field

4. **Performance:**
   - Validate theo batch (group by namespace)
   - Tránh validate quá nhiều namespaces cùng lúc
   - Monitor job progress

5. **Error Handling:**
   - Luôn check job status
   - Handle timeout gracefully
   - Provide meaningful error messages

## Các Files Liên Quan

### Backend
- `ValidationResource.java` - API endpoints
- `CNFChecklistService.java` - Conversion logic
- `AsyncValidationExecutor.java` - Async execution
- `CNFChecklistExcelGenerator.java` - Excel generation
- `K8sDataCollector.java` - K8s data collection (V1)
- `K8sDataCollectorV2.java` - Semantic collection (V2)
- `NamespaceComparator.java` - Comparison logic (V1)
- `ValidationServiceV2.java` - Semantic comparison (V2)

### Frontend
- `CNFChecklistPage.tsx` - Input & submission
- `CnfChecklistResults.tsx` - Results display
- `CnfValidationResults.tsx` - Results rendering
- `api.ts` - API client

### Models
- `CNFChecklistItem.java` - Single checklist item
- `CNFChecklistRequest.java` - Request wrapper
- `CnfComparison.java` - CNF-specific result format
- `NamespaceComparison.java` - Standard comparison result

## Kết Luận

Tính năng CNF Checklist Validation cung cấp một flow hoàn chỉnh từ input → processing → results:

1. **Flexible Input**: JSON/Table/Excel
2. **Robust Validation**: Multi-level validation
3. **Async Processing**: Non-blocking execution
4. **Smart Comparison**: Support multiple matching strategies
5. **Rich Results**: Excel + JSON reports
6. **User-Friendly UI**: Real-time progress, clear visualization

Với tài liệu này, bạn có thể:
- Hiểu rõ luồng thực thi end-to-end
- Debug issues hiệu quả
- Customize behavior theo nhu cầu
- Extend features mới

---

**Generated:** 2026-01-26  
**Version:** 1.0  
**Author:** GitHub Copilot
