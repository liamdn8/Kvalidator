# CNF Checklist Validation Feature - Implementation Summary

## Overview
Extended the KValidator API and UI to support CNF (Cloud-Native Function) checklist validation. This allows users to validate Kubernetes configurations against a checklist of expected values from MANO (Management and Orchestration) or design documents.

## Request Format
Users can submit validation requests with the following JSON structure:

```json
[
  {
    "namespace": "default",
    "kind": "Deployment",
    "objectName": "abm_01",
    "fieldKey": "spec.template.spec.containers[0].image",
    "manoValue": "harbor.local/vmano/webmano:1.2.3"
  },
  {
    "namespace": "default",
    "kind": "ConfigMap",
    "objectName": "abm-config",
    "fieldKey": "data.ACTUAL_VERSION",
    "manoValue": "v1.0.0"
  }
]
```

Each item specifies:
- **namespace**: Kubernetes namespace where the object exists
- **kind**: Resource type (Deployment, ConfigMap, Service, etc.)
- **objectName**: Name of the Kubernetes object
- **fieldKey**: Field path to validate (e.g., `spec.template.spec.containers[0].image`)
- **manoValue**: Expected value from MANO/design specification

## Backend Implementation

### 1. Data Models (`src/main/java/com/nfv/validator/model/cnf/`)

#### CNFChecklistItem.java
- Represents a single validation item
- Contains validation logic for required fields
- Provides unique ID generation

#### CNFChecklistRequest.java
- Container for list of checklist items
- Validates entire request structure
- Optional description and cluster fields

### 2. Service Layer (`src/main/java/com/nfv/validator/service/`)

#### CNFChecklistService.java
- Converts CNF checklist format to internal baseline format
- Groups items by namespace
- Creates FlatObjectModel instances with expected values
- Handles field mapping (metadata vs spec)

### 3. API Endpoint (`src/main/java/com/nfv/validator/api/ValidationResource.java`)

**POST** `/kvalidator/api/validate/cnf-checklist`

Request body:
```json
{
  "items": [...],
  "description": "Optional description",
  "cluster": "Optional cluster name"
}
```

Response:
```json
{
  "jobId": "cnf-checklist-20260115-123456",
  "status": "PENDING",
  "submittedAt": "2026-01-15T12:34:56Z",
  ...
}
```

### 4. Async Execution (`src/main/java/com/nfv/validator/service/AsyncValidationExecutor.java`)

#### executeCNFChecklistAsync()
- Runs validation in background thread
- Prevents blocking of API calls

#### executeCNFChecklistValidation()
- Converts checklist items to baseline namespace models
- Collects actual data from Kubernetes cluster
- Performs comparison for each namespace
- Generates Excel and JSON reports
- Updates job status and progress

### Flow:
1. Convert CNF checklist items to baseline objects
2. Group by namespace
3. For each namespace:
   - Fetch actual Kubernetes resources
   - Compare baseline (expected values) vs actual
   - Track differences
4. Generate Excel and JSON reports
5. Update job status to COMPLETED

## Frontend Implementation

### 1. Types (`frontend/src/types/cnf.ts`)

```typescript
interface CNFChecklistItem {
  namespace: string;
  kind: string;
  objectName: string;
  fieldKey: string;
  manoValue: string;
}

interface CNFChecklistRequest {
  items: CNFChecklistItem[];
  description?: string;
  cluster?: string;
}
```

### 2. API Service (`frontend/src/services/api.ts`)

Added `submitCNFChecklistValidation()` method:
```typescript
submitCNFChecklistValidation: async (request: CNFChecklistRequest): Promise<ValidationJobResponse>
```

### 3. UI Page (`frontend/src/pages/CNFChecklistPage.tsx`)

#### Features:
- **Two Input Modes:**
  1. **JSON Paste**: Paste checklist JSON directly
  2. **Table Input**: Add items one by one using a form

#### JSON Mode:
- Text area for pasting JSON
- "Load Sample JSON" button for example data
- Format validation with helpful error messages
- Info alert showing required fields

#### Table Mode:
- Form to add individual items
- Table displaying all added items
- Delete functionality for each row
- Inline editing capability

#### Common Features:
- Real-time job status display
- Progress bar showing validation progress
- Download Excel report button
- Display validation results using existing ValidationResults component
- Error handling and user feedback

### 4. Navigation (`frontend/src/App.tsx` & `frontend/src/layouts/MainLayout.tsx`)

Added new route `/cnf-checklist` with menu item "CNF Checklist" in the main navigation.

## Usage Example

### Via UI:
1. Navigate to "CNF Checklist" in the menu
2. Choose input mode (JSON or Table)
3. Enter checklist items
4. Click "Start Validation"
5. Monitor progress
6. View results and download Excel report

### Via API:
```bash
curl -X POST http://localhost:8080/kvalidator/api/validate/cnf-checklist \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "namespace": "default",
        "kind": "Deployment",
        "objectName": "my-app",
        "fieldKey": "spec.replicas",
        "manoValue": "3"
      }
    ],
    "description": "Production validation"
  }'
```

Response:
```json
{
  "jobId": "cnf-123456",
  "status": "PENDING",
  "submittedAt": "2026-01-15T10:30:00Z"
}
```

Then poll for status:
```bash
curl http://localhost:8080/kvalidator/api/validate/cnf-123456
```

Download report when complete:
```bash
curl -O http://localhost:8080/kvalidator/api/validate/cnf-123456/download
```

## Benefits

1. **Flexible Input**: Users can paste JSON or use table interface
2. **Async Processing**: Long-running validations don't block the UI
3. **Comprehensive Reports**: Excel and JSON formats for different audiences
4. **Reusable Components**: Leverages existing validation infrastructure
5. **Type Safety**: Full TypeScript types on frontend, Java DTOs on backend
6. **Validation**: Input validation at multiple levels (frontend, backend, service)

## Testing

The implementation integrates with the existing KValidator infrastructure, so validation logic, report generation, and job management are already tested. To test the new feature:

1. **Backend**: 
   - Start the Quarkus application
   - POST to `/kvalidator/api/validate/cnf-checklist`

2. **Frontend**:
   - Build frontend: `cd frontend && npm run build`
   - Navigate to `/kvalidator/web/cnf-checklist`
   - Test both input modes

3. **Integration**:
   - Submit CNF checklist
   - Verify job creation
   - Check progress updates
   - Download and verify reports

## Files Modified/Created

### Backend:
- `src/main/java/com/nfv/validator/model/cnf/CNFChecklistItem.java` (NEW)
- `src/main/java/com/nfv/validator/model/cnf/CNFChecklistRequest.java` (NEW)
- `src/main/java/com/nfv/validator/service/CNFChecklistService.java` (NEW)
- `src/main/java/com/nfv/validator/api/ValidationResource.java` (MODIFIED)
- `src/main/java/com/nfv/validator/service/AsyncValidationExecutor.java` (MODIFIED)
- `src/main/java/com/nfv/validator/model/api/ValidationJobResponse.java` (MODIFIED)

### Frontend:
- `frontend/src/types/cnf.ts` (NEW)
- `frontend/src/pages/CNFChecklistPage.tsx` (NEW)
- `frontend/src/services/api.ts` (MODIFIED)
- `frontend/src/App.tsx` (MODIFIED)
- `frontend/src/layouts/MainLayout.tsx` (MODIFIED)
- `frontend/src/types/index.ts` (MODIFIED)

## Next Steps

1. Build and test the backend
2. Build and test the frontend
3. Test full integration
4. Add documentation to README
5. Consider adding export to different formats (YAML, CSV)
