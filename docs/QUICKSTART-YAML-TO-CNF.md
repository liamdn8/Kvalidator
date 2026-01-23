# YAML to CNF Checklist Converter - Quick Start

## Giới thiệu

Tính năng mới cho phép **tự động tạo CNF Checklist Excel từ Kubernetes YAML files**. Thay vì phải manually tạo checklist, bạn chỉ cần upload YAML files và hệ thống sẽ tự động extract các trường quan trọng.

## Cách sử dụng

### Bước 1: Truy cập trang YAML to CNF

Mở web browser và truy cập:
```
http://localhost:8080/kvalidator/web/yaml-to-cnf
```

Hoặc click vào menu **"YAML to CNF"** trên thanh navigation.

### Bước 2: Nhập VIM Name

Nhập tên VIM/Cluster của bạn vào ô input:
```
Ví dụ: vim-hanoi, vim-hcm, cluster-prod
```

### Bước 3: Upload YAML File

1. Click button **"Select YAML File"**
2. Chọn file YAML của bạn (có thể chứa nhiều resources)
3. File sẽ được load và hiển thị preview
4. Hệ thống tự động extract danh sách namespaces

### Bước 4: Chọn Namespaces (Optional)

- Xem bảng namespaces đã tìm thấy
- Chọn namespaces muốn export (hoặc để trống = export tất cả)
- Mỗi namespace hiển thị:
  - Tên namespace
  - Số lượng resources
  - Loại resources (Deployment, Service, ConfigMap, etc.)

### Bước 5: Generate Excel

Click button **"Generate & Download Excel"**

File Excel sẽ tự động download với tên:
```
cnf-checklist-{vimName}-{timestamp}.xlsx
```

## Example

### Input: Kubernetes YAML

```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-app
  namespace: production
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
kind: ConfigMap
metadata:
  name: app-config
  namespace: production
data:
  LOG_LEVEL: "INFO"
  ENVIRONMENT: "production"
```

### Output: Excel File

| VIM Name   | Namespace  | Kind       | Object Name | Field Key                                          | Expected Value (MANO) |
|------------|------------|------------|-------------|----------------------------------------------------|-----------------------|
| vim-hanoi  | production | Deployment | web-app     | spec.replicas                                      | 3                     |
| vim-hanoi  | production | Deployment | web-app     | spec.template.spec.containers[0].image             | nginx:1.21            |
| vim-hanoi  | production | Deployment | web-app     | spec.template.spec.containers[0].resources.limits.memory | 512Mi          |
| vim-hanoi  | production | Deployment | web-app     | spec.template.spec.containers[0].resources.limits.cpu    | 500m           |
| vim-hanoi  | production | ConfigMap  | app-config  | data.LOG_LEVEL                                     | INFO                  |
| vim-hanoi  | production | ConfigMap  | app-config  | data.ENVIRONMENT                                   | production            |

## Các trường được Extract tự động

### Deployment
- Number of replicas
- Container image
- Image pull policy
- Resource limits (CPU, Memory)
- Resource requests (CPU, Memory)

### Service
- Service type (ClusterIP, NodePort, LoadBalancer)
- Port configuration
- Target port
- Protocol

### ConfigMap & Secret
- Tất cả data fields

### StatefulSet
- Replicas
- Service name
- Container image
- Volume claim templates

### DaemonSet
- Container image
- Update strategy

### PersistentVolumeClaim
- Access modes
- Storage size
- Storage class name

## Sử dụng Excel file đã generate

Sau khi có file Excel:

1. **Review và Edit**
   - Mở file Excel
   - Review các trường đã extract
   - Thêm/xóa/sửa các trường nếu cần

2. **Upload vào CNF Checklist**
   - Truy cập trang "CNF Checklist"
   - Click "Upload Excel"
   - Chọn file Excel vừa generate
   - Submit validation

3. **Validate với Cluster**
   - Hệ thống sẽ so sánh giá trị expected (từ Excel) với giá trị actual (từ cluster)
   - Hiển thị kết quả validation

## API Usage

Nếu bạn muốn sử dụng API trực tiếp:

### Extract Namespaces

```bash
curl -X POST http://localhost:8080/kvalidator/api/yaml-to-cnf/extract-namespaces \
  -F "file=@your-k8s-resources.yaml" \
  -F "fileName=your-k8s-resources.yaml"
```

Response:
```json
{
  "success": true,
  "message": "Found 2 namespace(s) in YAML file",
  "namespaces": [
    {
      "name": "default",
      "resourceCount": 3,
      "resourceKinds": "Deployment, Service"
    },
    {
      "name": "production",
      "resourceCount": 5,
      "resourceKinds": "Deployment, Service, ConfigMap"
    }
  ]
}
```

### Convert to Excel

```bash
curl -X POST http://localhost:8080/kvalidator/api/yaml-to-cnf/convert-to-excel \
  -H "Content-Type: application/json" \
  -d '{
    "vimName": "vim-hanoi",
    "yamlContent": "apiVersion: apps/v1\nkind: Deployment\n...",
    "namespaces": ["production"]
  }' \
  --output cnf-checklist.xlsx
```

## Tips & Best Practices

1. **Multiple Namespaces**: Nếu YAML có nhiều namespaces, chọn chỉ những namespaces cần validate để file Excel gọn hơn

2. **Edit Excel**: Sau khi generate, bạn có thể:
   - Xóa các trường không quan trọng
   - Thêm các trường custom
   - Sửa expected values nếu cần

3. **Large YAML Files**: Với file YAML lớn (>100 resources), nên:
   - Chọn specific namespaces thay vì "all"
   - Hoặc chia nhỏ YAML thành nhiều file

4. **Validation Workflow**:
   ```
   YAML Files → Generate Excel → Review/Edit → Upload to CNF Checklist → Validate
   ```

## Troubleshooting

### File không upload được
- Kiểm tra file có extension .yaml hoặc .yml
- Kiểm tra file có valid YAML format

### Không có items nào được generate
- Kiểm tra YAML có chứa valid Kubernetes resources
- Kiểm tra namespace filter có đúng không

### Excel download không thành công
- Kiểm tra VIM name đã nhập chưa
- Kiểm tra YAML content có hợp lệ

## Support

Nếu gặp vấn đề, kiểm tra:
- Backend logs: `/tmp/.kvalidator/results/`
- Browser console: F12 → Console tab
- API response errors

Hoặc xem documentation đầy đủ tại: [docs/YAML_TO_CNF_FEATURE.md](../docs/YAML_TO_CNF_FEATURE.md)
