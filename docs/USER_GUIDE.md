# Hướng dẫn sử dụng KValidator (User Guide)

## Giới thiệu

KValidator là công cụ command-line để kiểm tra và validate cấu hình NFV Infrastructure trên Kubernetes clusters.

## Cài đặt (Installation)

### Yêu cầu hệ thống
- Java 11 hoặc cao hơn
- Maven 3.6+ (để build từ source)
- Quyền truy cập vào Kubernetes clusters cần validate

### Build từ source

```bash
# Clone repository
git clone <repository-url>
cd Kvalidator

# Build với Maven
mvn clean package

# JAR file sẽ được tạo tại
# target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### Chạy ứng dụng

```bash
# Sử dụng Maven
mvn exec:java -Dexec.mainClass="com.nfv.validator.KValidatorApplication" -Dexec.args="--help"

# Hoặc chạy JAR file trực tiếp
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help
```

## Các tính năng chính

### 1. Design Validation - Đối chiếu thiết kế với thực tế

Kiểm tra xem môi trường thực tế có khớp với thiết kế ban đầu hay không.

#### Cú pháp

```bash
kvalidator -d <design-spec-file> -k <kubeconfig-file> [options]
```

#### Ví dụ

```bash
# Validate production cluster với design specification
java -jar kvalidator.jar \
  -d design-spec-production.yaml \
  -k ~/.kube/config-prod \
  -o json \
  -v

# Với output là HTML report
java -jar kvalidator.jar \
  -d design-spec-production.yaml \
  -k ~/.kube/config-prod \
  -o html > validation-report.html
```

#### Design Specification File Format

File thiết kế sử dụng định dạng YAML:

```yaml
version: "1.0"
designName: "NFV Production Environment"

cluster:
  name: "nfv-prod-cluster"
  version: "1.28"
  nodeCount: 
    min: 3
    max: 10

workloads:
  - name: "vnf-core"
    namespace: "nfv-core"
    replicas: 3
    resources:
      requests:
        cpu: "2000m"
        memory: "4Gi"
      limits:
        cpu: "4000m"
        memory: "8Gi"

# Xem file example đầy đủ tại:
# src/main/resources/examples/design-spec-example.yaml
```

### 2. Environment Comparison - So sánh các môi trường

So sánh cấu hình giữa nhiều môi trường (Production, Staging, Development).

#### Cú pháp

```bash
kvalidator -c <env1>,<env2>,... -k <kubeconfig1>,<kubeconfig2>,... [options]
```

#### Ví dụ

```bash
# So sánh Production và Staging
java -jar kvalidator.jar \
  -c production,staging \
  -k ~/.kube/config-prod,~/.kube/config-staging \
  -o json \
  -v

# So sánh 3 môi trường
java -jar kvalidator.jar \
  -c prod,staging,dev \
  -k config-prod.yaml,config-staging.yaml,config-dev.yaml \
  -o yaml > comparison-result.yaml
```

## Command Line Options

| Option | Long Option | Description | Required |
|--------|-------------|-------------|----------|
| `-d` | `--design-validation` | Design specification file path | For design validation |
| `-c` | `--compare-environments` | Environment names (comma-separated) | For comparison |
| `-k` | `--kubeconfig` | Kubeconfig file paths (comma-separated) | Yes |
| `-o` | `--output` | Output format: json, yaml, html | No (default: json) |
| `-v` | `--verbose` | Enable verbose logging | No |
| `-h` | `--help` | Display help information | No |

## Configuration

### Validation Rules Configuration

Tùy chỉnh validation rules trong file `validation-config.yaml`:

```yaml
version: "1.0"

settings:
  strictMode: false
  timeoutSeconds: 300
  outputFormat: "json"
  continueOnError: true

rules:
  - id: "NFV-001"
    name: "CPU Resource Limits"
    severity: "HIGH"
    enabled: true
    parameters:
      minCpuLimit: "100m"
      maxCpuLimit: "8000m"
```

### Logging Configuration

Cấu hình logging trong `src/main/resources/logback.xml`.

Log files được lưu tại: `logs/kvalidator.log`

## Output Formats

### JSON Format

```json
{
  "validationType": "DESIGN_VALIDATION",
  "targetCluster": "nfv-prod-cluster",
  "timestamp": "2026-01-12T10:30:00",
  "overallStatus": "FAILED",
  "issues": [
    {
      "id": "NFV-001-001",
      "severity": "HIGH",
      "category": "Resource Management",
      "description": "CPU limit not defined",
      "resource": "deployment/vnf-core",
      "expected": "4000m",
      "actual": "unlimited",
      "remediation": "Set CPU limit to 4000m in deployment spec"
    }
  ],
  "metrics": {
    "totalChecks": 25,
    "passedChecks": 20,
    "failedChecks": 5,
    "executionTimeMs": 3500
  }
}
```

### YAML Format

```yaml
validationType: DESIGN_VALIDATION
targetCluster: nfv-prod-cluster
timestamp: 2026-01-12T10:30:00
overallStatus: FAILED
issues:
  - id: NFV-001-001
    severity: HIGH
    category: Resource Management
    description: CPU limit not defined
    # ...
```

## Best Practices

### 1. Sử dụng Separate Kubeconfig Files
- Tạo kubeconfig riêng cho mỗi môi trường
- Sử dụng service account với read-only permissions
- Không dùng admin credentials

### 2. Version Control cho Design Specs
- Lưu design specification trong Git
- Tag theo version của hệ thống
- Review changes trước khi apply

### 3. Regular Validation
- Chạy validation định kỳ (daily/weekly)
- Integrate vào CI/CD pipeline
- Alert khi phát hiện drift

### 4. Audit Trail
- Lưu lại validation reports
- Track changes over time
- Document remediation actions

## Troubleshooting

### Không kết nối được Kubernetes cluster

```bash
# Kiểm tra kubeconfig
kubectl --kubeconfig=<path> cluster-info

# Kiểm tra permissions
kubectl --kubeconfig=<path> auth can-i get pods --all-namespaces
```

### Out of Memory Error

```bash
# Tăng heap size
java -Xmx2g -jar kvalidator.jar ...
```

### SSL Certificate Issues

```bash
# Nếu gặp SSL verification errors
# Cần update certificates hoặc configure trust store
```

## Examples

### Example 1: Validate Production Environment

```bash
#!/bin/bash
# validate-prod.sh

DESIGN_SPEC="designs/nfv-prod-design-v2.0.yaml"
KUBECONFIG="~/.kube/config-production"
OUTPUT_FILE="reports/validation-$(date +%Y%m%d).json"

java -jar kvalidator.jar \
  --design-validation "$DESIGN_SPEC" \
  --kubeconfig "$KUBECONFIG" \
  --output json \
  --verbose > "$OUTPUT_FILE"

# Check exit code
if [ $? -eq 0 ]; then
  echo "Validation completed successfully"
else
  echo "Validation failed - check $OUTPUT_FILE"
  exit 1
fi
```

### Example 2: Compare All Environments

```bash
#!/bin/bash
# compare-envs.sh

java -jar kvalidator.jar \
  --compare-environments production,staging,development \
  --kubeconfig configs/prod.yaml,configs/staging.yaml,configs/dev.yaml \
  --output html > reports/comparison-$(date +%Y%m%d).html

echo "Comparison report generated"
```

## Support

Để được hỗ trợ:
1. Kiểm tra documentation trong thư mục `docs/`
2. Xem example files trong `src/main/resources/examples/`
3. Enable verbose mode (`-v`) để xem detailed logs
4. Kiểm tra log file tại `logs/kvalidator.log`

## Next Steps

1. Đọc [REQUIREMENTS.md](REQUIREMENTS.md) để hiểu chi tiết về các tính năng
2. Xem [ARCHITECTURE.md](ARCHITECTURE.md) để hiểu cấu trúc code
3. Customize validation rules trong `validation-config.yaml`
4. Tạo design specification files cho môi trường của bạn
