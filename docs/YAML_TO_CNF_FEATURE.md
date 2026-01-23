# YAML to CNF Checklist Converter Feature

## Tổng quan

Tính năng mới cho phép **chuyển đổi Kubernetes YAML files sang CNF Checklist Excel file**. Đây là tính năng ngược lại với CNF Checklist upload - thay vì import checklist để validate, giờ bạn có thể **generate checklist từ YAML files**.

### Mục đích
- Hỗ trợ người dùng tạo bộ file tham số input cho CNF checklist từ Kubernetes YAML
- Tự động extract các trường quan trọng từ mỗi resource
- Smart search và filter theo namespaces
- Export sang Excel format để dễ dàng chỉnh sửa và sử dụng

## Kiến trúc

### Backend Components

#### 1. Models

**YamlToCNFRequest.java**
```java
{
  "vimName": "vim-hanoi",          // Required: VIM/Cluster name
  "yamlContent": "...",             // Required: YAML content
  "namespaces": ["default", "prod"], // Optional: Filter namespaces
  "importantFields": [...]          // Optional: Custom fields to extract
}
```

**NamespaceInfo.java**
```java
{
  "name": "default",
  "resourceCount": 5,
  "resourceKinds": "Deployment, Service, ConfigMap"
}
```

#### 2. Service Layer

**YamlToCNFChecklistConverter.java**
- `extractNamespaces(yamlContent)`: Smart search - extract all namespaces from YAML
- `convertToCNFChecklist(vimName, yamlContent, namespaces, fields)`: Convert YAML to checklist items

**Tính năng chính:**
- Parse multiple YAML documents (separated by `---`)
- Handle Kubernetes List objects
- Extract important fields for each resource type:
  - **Deployment**: replicas, container image, resources, imagePullPolicy
  - **Service**: type, ports, protocol
  - **ConfigMap**: all data fields
  - **Secret**: type, all data fields
  - **StatefulSet**: replicas, serviceName, volumeClaimTemplates
  - **DaemonSet**: container image, updateStrategy
  - **PersistentVolumeClaim**: accessModes, storage, storageClassName
- Support array indexing: `spec.containers[0].image`
- Support wildcard for maps: `data.*` extracts all ConfigMap data fields

**CNFChecklistFileParser.java**
- Thêm method `generateExcelFromItems(List<CNFChecklistItem>)`: Generate Excel from checklist items
- Auto-size columns
- Header styling

#### 3. REST API

**YamlToCNFResource.java** - `/kvalidator/api/yaml-to-cnf`

**Endpoints:**

##### Extract Namespaces (Smart Search)
```
POST /yaml-to-cnf/extract-namespaces
Content-Type: multipart/form-data

Form Parameters:
- file: YAML file
- fileName: File name

Response:
{
  "success": true,
  "message": "Found 3 namespace(s) in YAML file",
  "namespaces": [
    {
      "name": "default",
      "resourceCount": 5,
      "resourceKinds": "Deployment, Service, ConfigMap"
    },
    ...
  ]
}
```

##### Convert YAML to Excel
```
POST /yaml-to-cnf/convert-to-excel
Content-Type: application/json

Request Body:
{
  "vimName": "vim-hanoi",
  "yamlContent": "apiVersion: apps/v1\nkind: Deployment\n...",
  "namespaces": ["default", "production"],  // Optional
  "importantFields": [...]                   // Optional
}

Response: Excel file (.xlsx)
Content-Disposition: attachment; filename="cnf-checklist-vim-hanoi-20260123-143022.xlsx"
X-Item-Count: 25
```

### Frontend Components

#### 1. API Service Updates

**frontend/src/services/api.ts**

Thêm 2 methods mới:
```typescript
// Extract namespaces from YAML file
extractNamespacesFromYaml(file: File): Promise<{
  success: boolean;
  message: string;
  namespaces: NamespaceInfo[];
}>

// Convert YAML to Excel
convertYamlToExcel(params: {
  vimName: string;
  yamlContent: string;
  namespaces?: string[];
  importantFields?: string[];
}): Promise<Blob>
```

#### 2. UI Page

**frontend/src/pages/YamlToCNFPage.tsx**

**4-Step Workflow:**

1. **Step 1: Enter VIM Name**
   - Input field cho VIM/Cluster name (e.g., "vim-hanoi")

2. **Step 2: Upload YAML File**
   - Upload YAML file (.yaml, .yml)
   - Preview YAML content (editable TextArea)
   - Auto-extract namespaces khi upload

3. **Step 3: Select Namespaces (Smart Search)**
   - Hiển thị bảng namespaces tìm thấy
   - Multi-select dropdown để chọn namespaces
   - Nếu không chọn = extract tất cả namespaces
   - Table hiển thị: namespace name, resource count, resource kinds

4. **Step 4: Generate & Download**
   - Button "Generate & Download Excel"
   - Auto-download file với tên: `cnf-checklist-{vimName}-{timestamp}.xlsx`

**UI Features:**
- Card-based layout với clear steps
- Loading states cho mọi actions
- Error handling với user-friendly messages
- Help section với instructions
- Namespace table với Tags cho kinds
- Real-time YAML content editing

#### 3. Routing

**frontend/src/App.tsx**
- Thêm route: `/yaml-to-cnf` → `YamlToCNFPage`

**frontend/src/layouts/MainLayout.tsx**
- Thêm menu item "YAML to CNF" với icon FileSpreadsheet

## Workflow

### User Journey

1. **Truy cập trang "YAML to CNF"** từ menu
2. **Nhập VIM Name** (e.g., "vim-hanoi")
3. **Upload Kubernetes YAML file**
   - File được đọc và hiển thị preview
   - Tự động extract namespaces và hiển thị trong bảng
4. **Xem danh sách namespaces** (Smart Search)
   - Bảng hiển thị: namespace, số resources, loại resources
   - Chọn namespaces muốn export (hoặc để trống = all)
5. **Click "Generate & Download Excel"**
   - Backend parse YAML
   - Extract important fields từ mỗi resource
   - Generate Excel file
   - Auto-download về máy
6. **Mở Excel file**
   - Review các fields đã extract
   - Edit nếu cần
   - Sử dụng cho CNF validation

## Important Fields Extracted

Mỗi loại resource có bộ fields được extract mặc định:

### Deployment
- `spec.replicas`
- `spec.template.spec.containers[0].image`
- `spec.template.spec.containers[0].imagePullPolicy`
- `spec.template.spec.containers[0].resources.limits.memory`
- `spec.template.spec.containers[0].resources.limits.cpu`
- `spec.template.spec.containers[0].resources.requests.memory`
- `spec.template.spec.containers[0].resources.requests.cpu`

### Service
- `spec.type`
- `spec.ports[0].port`
- `spec.ports[0].targetPort`
- `spec.ports[0].protocol`

### ConfigMap
- `data.*` (tất cả data fields)

### Secret
- `type`
- `data.*` (tất cả data fields)

### StatefulSet
- `spec.replicas`
- `spec.serviceName`
- `spec.template.spec.containers[0].image`
- `spec.volumeClaimTemplates[0].spec.resources.requests.storage`

### DaemonSet
- `spec.template.spec.containers[0].image`
- `spec.updateStrategy.type`

### PersistentVolumeClaim
- `spec.accessModes[0]`
- `spec.resources.requests.storage`
- `spec.storageClassName`

## Custom Fields

Người dùng có thể chỉ định custom important fields trong request:
```json
{
  "vimName": "vim-hanoi",
  "yamlContent": "...",
  "importantFields": [
    "spec.replicas",
    "metadata.labels.version",
    "spec.template.spec.containers[0].env[0].value"
  ]
}
```

## Excel Output Format

```
| VIM Name   | Namespace | Kind       | Object Name  | Field Key                                    | Expected Value (MANO)        |
|------------|-----------|------------|--------------|----------------------------------------------|------------------------------|
| vim-hanoi  | default   | Deployment | web-app      | spec.replicas                                | 3                            |
| vim-hanoi  | default   | Deployment | web-app      | spec.template.spec.containers[0].image       | nginx:1.21                   |
| vim-hanoi  | default   | Service    | web-service  | spec.type                                    | ClusterIP                    |
| vim-hanoi  | default   | ConfigMap  | app-config   | data.LOG_LEVEL                               | INFO                         |
| vim-hanoi  | default   | ConfigMap  | app-config   | data.ENVIRONMENT                             | production                   |
```

## Example Usage

### Example YAML Input

```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-app
  namespace: default
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: nginx
        image: nginx:1.21
        resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: web-service
  namespace: default
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8080
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: default
data:
  LOG_LEVEL: "INFO"
  ENVIRONMENT: "production"
```

### Output Excel

Sẽ có 9 rows (7 items + header):
1. Header row
2. web-app - spec.replicas = 3
3. web-app - spec.template.spec.containers[0].image = nginx:1.21
4. web-app - spec.template.spec.containers[0].resources.limits.memory = 512Mi
5. web-app - spec.template.spec.containers[0].resources.limits.cpu = 500m
6. web-service - spec.type = ClusterIP
7. web-service - spec.ports[0].port = 80
8. app-config - data.LOG_LEVEL = INFO
9. app-config - data.ENVIRONMENT = production

## Error Handling

### Backend Validation
- VIM name required
- YAML content required
- Valid YAML format
- At least one valid Kubernetes resource

### Frontend Validation
- File upload errors
- Network errors
- Invalid YAML format
- No items generated
- User-friendly error messages with details

## Testing

### Manual Test Steps

1. **Test Basic Flow**
   ```bash
   curl -X POST http://localhost:8080/kvalidator/api/yaml-to-cnf/extract-namespaces \
     -F "file=@deployment.yaml" \
     -F "fileName=deployment.yaml"
   ```

2. **Test Convert to Excel**
   ```bash
   curl -X POST http://localhost:8080/kvalidator/api/yaml-to-cnf/convert-to-excel \
     -H "Content-Type: application/json" \
     -d '{
       "vimName": "vim-test",
       "yamlContent": "apiVersion: apps/v1\nkind: Deployment\n...",
       "namespaces": ["default"]
     }' \
     --output test-checklist.xlsx
   ```

3. **Test UI**
   - Navigate to http://localhost:8080/kvalidator/web/yaml-to-cnf
   - Upload sample YAML file
   - Verify namespaces extracted
   - Select namespaces
   - Generate Excel
   - Verify downloaded file

## Integration với existing CNF Checklist

Sau khi generate Excel từ YAML:
1. Mở file Excel, review và edit nếu cần
2. Upload lại vào "CNF Checklist" page
3. Submit validation job
4. So sánh với cluster thực tế

## Files Changed/Created

### Backend
- ✅ `src/main/java/com/nfv/validator/model/cnf/YamlToCNFRequest.java` (NEW)
- ✅ `src/main/java/com/nfv/validator/model/cnf/NamespaceInfo.java` (NEW)
- ✅ `src/main/java/com/nfv/validator/service/YamlToCNFChecklistConverter.java` (NEW)
- ✅ `src/main/java/com/nfv/validator/service/CNFChecklistFileParser.java` (UPDATED - thêm generateExcelFromItems)
- ✅ `src/main/java/com/nfv/validator/api/YamlToCNFResource.java` (NEW)

### Frontend
- ✅ `frontend/src/services/api.ts` (UPDATED - thêm 2 methods)
- ✅ `frontend/src/pages/YamlToCNFPage.tsx` (NEW)
- ✅ `frontend/src/App.tsx` (UPDATED - thêm route)
- ✅ `frontend/src/layouts/MainLayout.tsx` (UPDATED - thêm menu item)

### Documentation
- ✅ `docs/YAML_TO_CNF_FEATURE.md` (NEW - this file)

## Next Steps

1. ✅ **Code Implementation** - DONE
2. **Testing**
   - Unit tests cho YamlToCNFChecklistConverter
   - Integration tests cho API endpoints
   - Frontend E2E tests
3. **Documentation**
   - Update README.md
   - Update API documentation
4. **Deployment**
   - Build và test local
   - Deploy to staging
   - User acceptance testing

## Benefits

1. **Tự động hóa**: Không cần manually tạo checklist từ YAML
2. **Chính xác**: Extract đúng các fields quan trọng
3. **Tiết kiệm thời gian**: Generate trong vài giây thay vì vài giờ manual work
4. **Smart Search**: Tự động tìm và hiển thị namespaces
5. **Linh hoạt**: Có thể chọn namespaces hoặc custom fields
6. **Integration**: Kết hợp trơn tru với existing CNF Checklist validation

## Notes

- Wildcard pattern `data.*` chỉ hoạt động với top-level map fields
- Array indexing chỉ support single index (không support nested arrays)
- Default namespace là "default" nếu resource không chỉ định namespace
- Excel file được auto-sized columns để dễ đọc
- Filename pattern: `cnf-checklist-{vimName}-{timestamp}.xlsx`
