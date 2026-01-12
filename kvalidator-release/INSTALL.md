# KValidator Release Package

## Nội dung package

```
kvalidator-release/
├── kvalidator.jar                    # Executable JAR file (33MB)
├── validation-config.yaml             # File cấu hình ignore fields
├── baseline-example.yaml              # File ví dụ về baseline design
├── run.sh                             # Script chạy cho Linux/Mac
├── kvalidator.cmd                     # Script chạy cho Windows
├── README.md                          # Tài liệu đầy đủ
├── USAGE.md                           # Ví dụ sử dụng chi tiết
├── QUICKSTART.md                      # Hướng dẫn bắt đầu nhanh
└── LICENSE                            # License file
```

## Cài đặt

### Linux/Mac
```bash
# Giải nén
tar -xzf kvalidator-1.0.0-SNAPSHOT.tar.gz

# Di chuyển vào thư mục
cd kvalidator-release

# Chạy
./run.sh --help
```

### Windows
```cmd
REM Giải nén file kvalidator-1.0.0-SNAPSHOT.zip

REM Di chuyển vào thư mục
cd kvalidator-release

REM Chạy
kvalidator.cmd --help
```

## Yêu cầu

- **Java 11+**: Kiểm tra với `java -version`
- **kubectl configured**: Tool sử dụng kubeconfig để kết nối cluster
- **Quyền truy cập Kubernetes**: Cần quyền đọc (get, list) các resources

## Sử dụng nhanh

### 1. So sánh 2 namespaces

```bash
# Linux/Mac
./run.sh app-dev app-prod

# Windows
kvalidator.cmd app-dev app-prod
```

### 2. So sánh với baseline design

```bash
# Linux/Mac
./run.sh -b baseline-example.yaml -v app-dev

# Windows
kvalidator.cmd -b baseline-example.yaml -v app-dev
```

### 3. Export kết quả ra Excel

```bash
# Linux/Mac
./run.sh -o report.xlsx app-dev app-staging app-prod

# Windows
kvalidator.cmd -o report.xlsx app-dev app-staging app-prod
```

## Tài liệu

- **QUICKSTART.md**: Bắt đầu nhanh với các lệnh cơ bản
- **README.md**: Tài liệu đầy đủ về tính năng và options
- **USAGE.md**: Các use cases thực tế và best practices

## Cấu hình

File `validation-config.yaml` chứa danh sách các fields được ignore khi so sánh. Bạn có thể tùy chỉnh:

```yaml
ignoreFields:
  - "metadata.creationTimestamp"
  - "metadata.uid"
  - "status"
  # Thêm fields cần ignore cho project của bạn
```

## Baseline file format

Baseline YAML file có định dạng standard Kubernetes manifests:

```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-deployment
spec:
  replicas: 3
  # ... spec khác
---
apiVersion: v1
kind: Service
metadata:
  name: app-service
spec:
  # ... spec
```

## Ví dụ workflow

### 1. Kiểm tra consistency giữa các môi trường

```bash
./run.sh -o env-check.xlsx app-dev app-staging app-prod
```

Mở file `env-check.xlsx` để xem comparison matrix.

### 2. Validate production với thiết kế

```bash
# Tạo file baseline từ thiết kế
# File: production-baseline.yaml

# Chạy validation
./run.sh -b production-baseline.yaml -o prod-validation.xlsx app-prod

# Review kết quả trong Excel
```

### 3. Quick spot check

```bash
./run.sh -v app-dev app-prod
```

Output hiển thị ngay trên console.

## Troubleshooting

### Error: "kubectl: command not found"
- Cài đặt kubectl: https://kubernetes.io/docs/tasks/tools/

### Error: "Failed to connect to cluster"
- Kiểm tra kubectl context: `kubectl config current-context`
- Verify access: `kubectl cluster-info`

### Error: "validation-config.yaml not found"
- Script tự động copy file config từ package
- Hoặc copy manual: `cp validation-config.yaml .`

## Support

Đọc tài liệu đầy đủ trong:
- README.md - Tính năng và options
- USAGE.md - Use cases và examples
- QUICKSTART.md - Bắt đầu nhanh

## Version

KValidator v1.0.0-SNAPSHOT

Built with:
- Java 11
- Fabric8 Kubernetes Client 6.9.2
- Apache POI 5.2.5
- Jackson 2.15.3
