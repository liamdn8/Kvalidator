# Batch Validation - Quick Start

## Overview

KValidator giờ đây hỗ trợ **Batch Validation Mode** - chạy nhiều validation/comparison cùng lúc từ một file cấu hình.

## Quick Example

**1. Tạo file `validation-request.yaml`:**

```yaml
version: "1.0"
description: "Validate multiple environments"

settings:
  maxParallelRequests: 2
  outputDirectory: "reports"

requests:
  - name: "dev-comparison"
    type: "namespace-comparison"
    namespaces:
      - "cluster1/app-dev"
      - "cluster2/app-dev"
    output: "dev-comparison.xlsx"
  
  - name: "staging-comparison"
    type: "namespace-comparison"
    namespaces:
      - "cluster1/app-staging"
      - "cluster2/app-staging"
    output: "staging-comparison.xlsx"
```

**2. Chạy batch validation:**

```bash
java -jar kvalidator.jar -r validation-request.yaml
```

**3. Kết quả:**

```
╔══════════════════════════════════════════════════════════════════╗
║       KValidator - Batch Validation Mode                         ║
╚══════════════════════════════════════════════════════════════════╝

📁 Loading batch request from: validation-request.yaml
   ✓ Loaded 2 validation requests

═══════════════════════════════════════════════════════════════
  Executing request 1/2: dev-comparison
═══════════════════════════════════════════════════════════════

...

╔══════════════════════════════════════════════════════════════════╗
║                   Batch Execution Summary                         ║
╚══════════════════════════════════════════════════════════════════╝

Total Requests:      2
✅ Successful:       2
❌ Failed:           0

Individual Results:
─────────────────────────────────────────────────────────────────
dev-comparison                           ✅ SUCCESS
   Output: reports/dev-comparison.xlsx
   Objects compared: 15, Differences: 3
   Execution time: 2.34 seconds

staging-comparison                       ✅ SUCCESS
   Output: reports/staging-comparison.xlsx
   Objects compared: 18, Differences: 1
   Execution time: 1.89 seconds
```

## File Examples

- `validation-request-example.yaml` - Full featured example
- `validation-request-example.json` - JSON format example  
- `validation-request-simple.yaml` - Minimal example

## Documentation

Xem chi tiết tại: [docs/BATCH_VALIDATION.md](docs/BATCH_VALIDATION.md)

## Benefits

✅ **Tự động hóa**: Chạy nhiều validations trong 1 command
✅ **Parallel execution**: Tăng tốc độ khi có nhiều requests
✅ **CI/CD ready**: Dễ dàng tích hợp vào pipeline
✅ **API foundation**: Nền tảng cho REST API mode trong tương lai

## Use Cases

1. **Daily validation** cho tất cả môi trường
2. **Pre-deployment checks** trong CI/CD
3. **Multi-cluster health checks**
4. **Scheduled compliance validation**
