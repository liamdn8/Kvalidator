# KValidator - Usage Examples

## Quick Start

```bash
# 1. Đảm bảo có file validation-config.yaml trong thư mục hiện tại
cp src/main/resources/validation-config.yaml .

# 2. Chạy comparison đơn giản
java -jar kvalidator.jar app-dev app-prod
```

## Use Cases

### 1. So sánh 2 môi trường Dev và Prod

**Mục đích**: Kiểm tra xem dev và prod có cấu hình giống nhau không

```bash
java -jar kvalidator.jar -v app-dev app-prod
```

**Output example**:
```
🔍 Comparison Results:

┌─ Comparing: current/app-dev ↔ current/app-prod
│  Objects: 7 vs 7 (only in left: 0, only in right: 0, common: 7)
│  Match Rate: 42.9% (3 matched / 7 compared)
│  Differences: 4 objects with differences
│
│  📋 Detailed Differences:
│     Deployment (1 objects with diffs):
│       • app-deployment: 4 differences
│           - spec.replicas: [5] ≠ [3]
│           - metadata.labels.version: [v1.1.0] ≠ [v1.0.0]
│           - spec.template.spec.containers[0].image: [nginx:1.22] ≠ [nginx:1.21]
│           - spec.template.metadata.labels.version: [v1.1.0] ≠ [v1.0.0]
└─────────────────────────────────────────────────────────────────
```

### 2. So sánh 3 môi trường và export Excel

**Mục đích**: Tạo báo cáo comparison matrix cho team

```bash
java -jar kvalidator.jar -o comparison-matrix.xlsx \
  app-dev app-staging app-prod
```

**Kết quả**: File Excel với 2 sheets, dễ dàng review và share.

### 3. Validate triển khai với thiết kế ban đầu

**Mục đích**: Kiểm tra môi trường production có match với bản thiết kế không

**Bước 1**: Tạo file baseline YAML (ví dụ: `production-baseline.yaml`)

```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-deployment
  labels:
    app: myapp
    version: v1.0.0
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
        version: v1.0.0
    spec:
      containers:
      - name: app
        image: nginx:1.21
        ports:
        - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: app-service
spec:
  selector:
    app: myapp
  ports:
  - port: 80
    targetPort: 80
```

**Bước 2**: Chạy comparison

```bash
java -jar kvalidator.jar \
  -b production-baseline.yaml \
  -o validation-report.xlsx \
  app-prod
```

**Kết quả**: Excel report hiển thị tất cả config drift so với baseline.

### 4. So sánh chỉ Deployment và Service

**Mục đích**: Focus vào specific resource types

```bash
java -jar kvalidator.jar \
  -k Deployment,Service \
  -v \
  app-dev app-prod
```

### 5. So sánh từ nhiều Kubernetes clusters

**Mục đích**: Compare cùng namespace từ các cluster khác nhau

```bash
# Giả sử có 2 clusters trong kubeconfig: prod-cluster-1, prod-cluster-2
java -jar kvalidator.jar \
  -o multi-cluster-comparison.xlsx \
  prod-cluster-1/production \
  prod-cluster-2/production
```

### 6. So sánh với baseline directory

**Mục đích**: Baseline có nhiều YAML files trong folder

**Cấu trúc folder**:
```
design/
  ├── deployments.yaml
  ├── services.yaml
  ├── configmaps.yaml
  └── secrets.yaml
```

**Chạy**:
```bash
java -jar kvalidator.jar \
  -b design/ \
  -o baseline-validation.xlsx \
  app-dev app-staging app-prod
```

### 7. Custom validation config

**Mục đích**: Ignore thêm fields cụ thể cho project

**Tạo file `my-config.yaml`**:
```yaml
ignoreFields:
  - "metadata.creationTimestamp"
  - "metadata.resourceVersion"
  - "metadata.uid"
  - "status"
  # Custom ignores for your project
  - "metadata.labels.helm.sh/chart"
  - "metadata.labels.app.kubernetes.io/managed-by"
  - "spec.template.spec.serviceAccountName"
```

**Chạy**:
```bash
java -jar kvalidator.jar \
  -f my-config.yaml \
  -v \
  app-dev app-prod
```

## Advanced Scenarios

### Scenario 1: CI/CD Pipeline Integration

**Mục đích**: Tự động validate trong CI/CD

```bash
#!/bin/bash
# validate-deployment.sh

BASELINE_DIR="./k8s-design"
NAMESPACE="production"
REPORT="validation-report.xlsx"

# Run validation
java -jar kvalidator.jar \
  -b "$BASELINE_DIR" \
  -o "$REPORT" \
  "$NAMESPACE"

# Check if differences exist (simple check)
if [ $? -ne 0 ]; then
  echo "❌ Validation failed!"
  exit 1
fi

echo "✅ Validation passed - report saved to $REPORT"

# Upload to S3, send email, etc.
```

### Scenario 2: Multi-region consistency check

**Mục đích**: Đảm bảo các region có config giống nhau

```bash
java -jar kvalidator.jar \
  -o region-consistency.xlsx \
  us-west-cluster/app \
  us-east-cluster/app \
  eu-west-cluster/app \
  ap-south-cluster/app
```

### Scenario 3: Incremental deployment validation

**Mục đích**: Validate từng bước khi deploy progressive

```bash
# Step 1: Compare canary vs stable
java -jar kvalidator.jar -v \
  app-stable app-canary

# Step 2: If good, compare with baseline
java -jar kvalidator.jar \
  -b baseline.yaml \
  -o canary-validation.xlsx \
  app-canary
```

## Tips & Best Practices

### 1. Luôn dùng validation config
- Tránh false positives từ các field không quan trọng
- Tập trung vào business-critical configs

### 2. Sử dụng baseline cho production
- Maintain baseline YAML files trong Git
- Version control cho design documents
- Review baseline cùng code reviews

### 3. Export Excel cho non-technical stakeholders
- Dễ hiểu hơn console output
- Color coding giúp quick visual scan
- Share qua email, Confluence, etc.

### 4. Kết hợp với kubectl
```bash
# Quick fix after finding differences
kubectl get deployment app-deployment -n app-dev -o yaml > current-dev.yaml
# Review và apply changes
```

### 5. Scheduling periodic checks
```bash
# Cron job để check daily
0 2 * * * cd /path/to/kvalidator && \
  java -jar kvalidator.jar -o /reports/daily-$(date +\%Y\%m\%d).xlsx \
  app-dev app-staging app-prod
```

## Interpreting Results

### Match Rate
- **100%**: Perfect match - configurations identical
- **>80%**: Good - minor differences only
- **50-80%**: Review needed - significant differences
- **<50%**: Alert - major drift detected

### Common Differences (usually safe to ignore)
- `metadata.creationTimestamp`
- `metadata.resourceVersion`
- `metadata.uid`
- `status.*` fields
- Auto-generated annotations

### Critical Differences (need attention)
- `spec.replicas` - Scale differences
- `spec.template.spec.containers[].image` - Version mismatch
- `metadata.labels` - May affect selectors
- `spec.ports` - Network configuration
- `spec.env` - Environment variables

## Output Formats

### Console Output
- Quick feedback
- Good for CI/CD pass/fail
- Use with `-v` for details

### Excel Output
- Professional reports
- Easy filtering and sorting
- Visual diff with colors
- Good for documentation and audits

## Error Messages

### "Path does not exist: baseline.yaml"
**Solution**: Check file path, ensure `.yaml` extension

### "At least 2 namespaces required"
**Solution**: Provide at least 2 namespaces (or use `-b` for baseline mode)

### "Failed to collect namespace 'xxx'"
**Solution**: Check namespace exists, verify kubectl access

### "No YAML files found"
**Solution**: Ensure directory contains `.yaml` or `.yml` files

## Getting Help

```bash
# Show all options
java -jar kvalidator.jar --help

# Version info
java -jar kvalidator.jar --version
```

## Example Workflows

### Daily Operations
```bash
# Morning check: Compare yesterday vs today
java -jar kvalidator.jar -v yesterday-backup/ app-prod

# Quick spot check
java -jar kvalidator.jar app-dev app-prod
```

### Release Process
```bash
# Pre-release: Validate staging
java -jar kvalidator.jar -b production-baseline.yaml app-staging

# Post-release: Compare new prod with old prod
java -jar kvalidator.jar prod-backup/ app-prod

# Multi-env check
java -jar kvalidator.jar -o release-validation.xlsx \
  app-dev app-staging app-prod
```

### Audit & Compliance
```bash
# Generate compliance report
java -jar kvalidator.jar \
  -b compliance-baseline/ \
  -o compliance-report-$(date +%Y%m).xlsx \
  prod-namespace-1 prod-namespace-2 prod-namespace-3
```
