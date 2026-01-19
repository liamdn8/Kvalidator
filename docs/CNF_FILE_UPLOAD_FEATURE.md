# CNF Checklist File Upload Feature

## Tổng quan

Tính năng mới cho phép upload và parse file CNF Checklist từ hai dạng:
- **File JSON**: Upload file JSON với cấu trúc danh sách CNF checklist items
- **File Excel**: Upload file Excel (.xlsx/.xls) với cấu trúc bảng

## Backend Components

### 1. CNFChecklistFileParser Service
**Location**: `src/main/java/com/nfv/validator/service/CNFChecklistFileParser.java`

Service xử lý parsing file JSON và Excel:
- `parseJsonFile(byte[] fileContent)`: Parse JSON file thành danh sách CNFChecklistItem
- `parseExcelFile(byte[] fileContent)`: Parse Excel file thành danh sách CNFChecklistItem  
- `generateExcelTemplate()`: Tạo file Excel template mẫu để download

**Excel Format**:
- Row 1 (Header): VIM Name | Namespace | Kind | Object Name | Field Key | Expected Value (MANO)
- Row 2+: Dữ liệu checklist items

**Features**:
- Validation đầy đủ cho tất cả required fields
- Xử lý nhiều cell types (String, Numeric, Boolean, Formula)
- Error handling với thông báo chi tiết row number khi có lỗi
- Sample data trong template để người dùng tham khảo

### 2. CNFChecklistFileResource API
**Location**: `src/main/java/com/nfv/validator/api/CNFChecklistFileResource.java`

REST API endpoints cho file operations:

#### Upload JSON File
```
POST /kvalidator/api/cnf-checklist/upload/json
Content-Type: multipart/form-data

Form Parameters:
- file: File content (InputStream)
- fileName: File name (String)

Response:
{
  "success": true,
  "message": "Successfully parsed X items from JSON file",
  "itemCount": X,
  "items": [...]
}
```

#### Upload Excel File
```
POST /kvalidator/api/cnf-checklist/upload/excel
Content-Type: multipart/form-data

Form Parameters:
- file: File content (InputStream)
- fileName: File name (String)

Response:
{
  "success": true,
  "message": "Successfully parsed X items from Excel file",
  "itemCount": X,
  "items": [...]
}
```

#### Download Excel Template
```
GET /kvalidator/api/cnf-checklist/template/excel

Response: Excel file (.xlsx)
Content-Disposition: attachment; filename="cnf-checklist-template.xlsx"
```

## Frontend Components

### 1. API Service Updates
**Location**: `frontend/src/services/api.ts`

Thêm 3 methods mới:
- `uploadJsonFile(file: File)`: Upload và parse JSON file
- `uploadExcelFile(file: File)`: Upload và parse Excel file
- `downloadExcelTemplate()`: Download Excel template

### 2. CNFChecklistPage Updates
**Location**: `frontend/src/pages/CNFChecklistPage.tsx`

#### File Upload UI
- **Upload JSON Button**: Upload file JSON và tự động chuyển sang Table Input mode
- **Upload Excel Button**: Upload file Excel và tự động chuyển sang Table Input mode
- **Download Template Button**: Download file Excel mẫu

#### Table Enhancements
Thêm **Filter & Sorter** cho tất cả columns:

**VIM Name, Namespace, Kind**:
- Sorter: Alphabetical sorting
- Filter: Dropdown với danh sách unique values

**Object Name, Field Key, Expected Value**:
- Sorter: Alphabetical sorting
- Filter: Search input với text matching

**Features**:
- Real-time filtering và sorting
- Multiple filters có thể apply cùng lúc
- Preserved data khi switching modes

## Workflow

### Upload JSON File
1. User click "Upload JSON" button
2. Select JSON file từ file system
3. File được upload và parse ở backend
4. Nếu thành công:
   - Data load vào table
   - Auto switch sang "Table Input" mode
   - Show success message với số items parsed
5. Nếu lỗi: Show error message với chi tiết lỗi

### Upload Excel File
1. User click "Upload Excel" button
2. Select Excel file (.xlsx hoặc .xls)
3. File được upload và parse ở backend
4. Nếu thành công:
   - Data load vào table
   - Auto switch sang "Table Input" mode
   - Show success message với số items parsed
5. Nếu lỗi: Show error message với chi tiết lỗi (bao gồm row number nếu có)

### Download Template
1. User click "Download Template" button
2. Backend generate Excel file với:
   - Header row formatted
   - 4 sample data rows
   - Proper column widths
3. File download với tên "cnf-checklist-template.xlsx"
4. User có thể edit file này và upload lại

## Error Handling

### Backend Validation
- Empty file check
- File format validation (Excel: .xlsx/.xls only)
- Required fields validation cho mỗi item:
  - vimName (required)
  - namespace (required)
  - kind (required)
  - objectName (required)
  - fieldKey (required)
  - manoValue (required)
- Row-level error reporting cho Excel (e.g., "Error at row 5: Field Key is required")

### Frontend Error Display
- Network errors
- Parsing errors
- Validation errors
- User-friendly error messages

## Testing

### Test JSON Upload
```json
[
  {
    "vimName": "vim-hanoi",
    "namespace": "default",
    "kind": "Deployment",
    "objectName": "abm_01",
    "fieldKey": "spec.template.spec.containers[0].image",
    "manoValue": "harbor.local/vmano/webmano:1.2.3"
  }
]
```

### Test Excel Upload
1. Download template
2. Edit sample data
3. Upload file
4. Verify data loaded correctly

## Dependencies

### Backend
- Apache POI 5.2.5 (đã có trong pom.xml)
- Jackson for JSON parsing (đã có)
- Quarkus RESTEasy Reactive (đã có)

### Frontend
- Ant Design Upload component (đã có)
- Axios for API calls (đã có)

## Notes

- File upload sử dụng multipart/form-data
- Excel parser hỗ trợ cả .xlsx (OOXML) và .xls (HSSF)
- Numeric values trong Excel được convert sang String tự động
- Template Excel có sample data để guide người dùng
- Data được parse và validate ở backend để ensure data integrity
