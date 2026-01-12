# KValidator - NFV Infrastructure Validation Tool

Tool tự động kiểm tra và validate cấu hình trên môi trường ảo hóa và cloud cho viễn thông (NFV Infrastructure).

## Tính năng

### 1. Đối chiếu thiết kế hệ thống so với thực tế triển khai
- So sánh cấu hình Kubernetes từ file YAML design/baseline với môi trường đang chạy
- Phát hiện sự khác biệt giữa bản thiết kế và triển khai thực tế

### 2. Đối chiếu, so sánh các môi trường với nhau
- So sánh nhiều namespace/cluster với nhau (dev, staging, production)
- Hỗ trợ so sánh pairwise hoặc so với baseline
- Phát hiện inconsistency giữa các môi trường

### 3. Field filtering với config
- Ignore các trường không cần thiết (metadata.uid, status, v.v.)
- Config file YAML linh hoạt, có thể tùy chỉnh
- Hỗ trợ prefix matching (ví dụ: `metadata.annotations` match tất cả annotations)

### 4. Excel export
- Export kết quả ra file Excel với 2 sheets:
  - **Summary**: Ma trận so sánh tổng quan với color coding
  - **Details**: Chi tiết từng field khác biệt
- Dễ dàng chia sẻ và báo cáo

## Yêu cầu hệ thống

- Java 11 hoặc cao hơn
- Maven 3.x (để build từ source)
- kubectl configured (để kết nối Kubernetes clusters)

## Cài đặt

### Option 1: Sử dụng pre-built JAR

```bash
# Download JAR file
# File được build tại: target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar

# Copy validation config
cp src/main/resources/validation-config.yaml .

# Chạy tool
java -jar kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help
```

### Option 2: Build từ source

```bash
# Clone repository
git clone <repo-url>
cd Kvalidator

# Build
mvn clean package -DskipTests

# JAR file sẽ được tạo tại:
# target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

## Sử dụng

### 1. So sánh 2 hoặc nhiều namespaces

```bash
# So sánh 2 namespaces
java -jar kvalidator.jar app-dev app-prod

# So sánh 3 namespaces (pairwise comparison)
java -jar kvalidator.jar app-dev app-staging app-prod

# So sánh từ các cluster khác nhau
java -jar kvalidator.jar cluster1/app-dev cluster2/app-dev
```

### 2. So sánh với baseline (thiết kế)

```bash
# So sánh 1 namespace với baseline YAML file
java -jar kvalidator.jar -b baseline-design.yaml app-dev

# So sánh nhiều namespaces với baseline directory
java -jar kvalidator.jar -b design-folder/ app-dev app-staging app-prod
```

### 3. Export kết quả ra Excel

```bash
# Export comparison results
java -jar kvalidator.jar -o report.xlsx app-dev app-staging app-prod

# So sánh với baseline và export
java -jar kvalidator.jar -b baseline.yaml -o baseline-report.xlsx app-dev app-staging
```

### 4. Verbose mode (chi tiết)

```bash
# Hiển thị chi tiết tất cả differences
java -jar kvalidator.jar -v app-dev app-staging
```

### 5. Filter theo resource kinds

```bash
# Chỉ so sánh Deployment và Service
java -jar kvalidator.jar -k Deployment,Service app-dev app-prod
```

### 6. Custom validation config

```bash
# Sử dụng config file riêng
java -jar kvalidator.jar -f my-config.yaml app-dev app-prod
```

## Options

```
USAGE:
  java -jar kvalidator.jar [OPTIONS] namespace1 namespace2 [namespace3 ...]
  java -jar kvalidator.jar [OPTIONS] -b <baseline-path> namespace1 [namespace2 ...]

OPTIONS:
  -h, --help              Display help message
  -b, --baseline PATH     Path to baseline YAML file or directory
  -c, --cluster NAME      Default cluster name (default: current context)
  -k, --kinds KIND1,...   Resource kinds to compare (Deployment,Service,...)
  -v, --verbose           Show detailed comparison results
  -f, --config FILE       Path to validation config file (default: ./validation-config.yaml)
  -o, --output FILE       Export to Excel file (e.g., report.xlsx)
```

## Validation Config

File `validation-config.yaml` cho phép bạn ignore các field không cần so sánh:

```yaml
ignoreFields:
  - "metadata.creationTimestamp"
  - "metadata.generation"
  - "metadata.resourceVersion"
  - "metadata.uid"
  - "metadata.selfLink"
  - "metadata.managedFields"
  - "metadata.namespace"
  - "metadata.annotations"
  - "status"
  - "spec.clusterIP"
  - "spec.clusterIPs"
  # ... thêm các field khác
```

**Prefix matching**: Field `metadata.annotations` sẽ ignore tất cả fields bắt đầu bằng `metadata.annotations.*`

## Ví dụ thực tế

### Ví dụ 1: Kiểm tra consistency giữa các môi trường

```bash
java -jar kvalidator.jar -o env-comparison.xlsx \
  app-dev app-staging app-prod
```

**Kết quả**: File Excel với comparison matrix, dễ dàng phát hiện config khác nhau giữa dev/staging/prod.

### Ví dụ 2: Validate triển khai so với thiết kế

```bash
java -jar kvalidator.jar -b design-docs/ \
  -o validation-report.xlsx \
  production-namespace
```

**Kết quả**: So sánh môi trường production với file thiết kế, phát hiện drift.

### Ví dụ 3: Quick check với verbose

```bash
java -jar kvalidator.jar -v \
  -k Deployment,StatefulSet \
  app-dev app-staging
```

**Kết quả**: Hiển thị ngay trên console tất cả differences của Deployment và StatefulSet.

## Excel Report Structure

### Sheet 1: Summary
- Ma trận so sánh tổng quan
- Color coding:
  - 🟦 **BASELINE**: Object từ baseline
  - 🟢 **MATCH**: Hoàn toàn giống nhau
  - 🟠 **DIFFERENT**: Có sự khác biệt
  - 🔴 **MISSING**: Object không tồn tại

### Sheet 2: Details
- Chi tiết từng field khác biệt
- Columns: STT | Kind | Object Name | Field Key | Namespace1 Value | Namespace2 Value | ...
- Dễ dàng filter và analyze

## Supported Kubernetes Resources

- Deployment
- StatefulSet
- DaemonSet
- Service
- ConfigMap
- Secret
- Pod

## Troubleshooting

### Lỗi: "Path does not exist"
- Kiểm tra đường dẫn baseline YAML file/directory
- Đảm bảo file có extension `.yaml` hoặc `.yml`

### Lỗi: "Failed to connect to cluster"
- Kiểm tra kubectl context: `kubectl config current-context`
- Verify cluster access: `kubectl cluster-info`

### Lỗi: "No YAML files found"
- Kiểm tra directory có chứa file `.yaml` hoặc `.yml`
- Đảm bảo file YAML có cấu trúc đúng (kind, metadata, spec)

## Development

### Build
```bash
mvn clean package
```

### Run tests
```bash
mvn test
```

### Debug
```bash
# Set log level to DEBUG in logback.xml
mvn clean package
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar -v ...
```

## License

[LICENSE](LICENSE)

## Author

NFV Infrastructure Validation Tool

## Giới thiệu (Introduction)

KValidator là công cụ tự động kiểm tra và validate các cấu hình trên môi trường ảo hóa và triển khai cloud cho viễn thông (NFV Infrastructure). Công cụ hỗ trợ kết nối đồng thời tới nhiều Kubernetes clusters để thực hiện kiểm tra và so sánh.

KValidator is an automated checklist and configuration validation tool for virtualized environments and cloud deployments in telecommunications (NFV Infrastructure). The tool supports simultaneous connections to multiple Kubernetes clusters for validation and comparison.

## Tính năng chính (Key Features)

### 🔍 1. Design Validation - Đối chiếu thiết kế với thực tế
- So sánh cấu hình thực tế với thiết kế ban đầu
- Phát hiện drift và inconsistencies
- Báo cáo chi tiết với mức độ nghiêm trọng
- Hỗ trợ NFV-specific validation rules

### 🔄 2. Environment Comparison - So sánh môi trường
- So sánh nhiều môi trường (Prod, Staging, Dev)
- Phát hiện differences và similarities
- Matrix comparison view
- Standardization recommendations

### 🔗 3. Multi-Cluster Support
- Kết nối đồng thời tới nhiều Kubernetes clusters
- Multiple authentication methods
- Connection pooling và retry mechanisms
- Parallel processing cho hiệu năng tối ưu

## Công nghệ (Technology Stack)

- **Language**: Java 11
- **Build Tool**: Maven 3.x
- **Kubernetes Client**: Fabric8 Kubernetes Client 6.9.2
- **Configuration**: Jackson, SnakeYAML
- **Logging**: SLF4J + Logback
- **Testing**: JUnit 5, Mockito

## Cài đặt nhanh (Quick Start)

### Yêu cầu hệ thống (Prerequisites)
```bash
# Java 11 or higher
java -version

# Maven 3.6 or higher
mvn -version
```

### Build project

```bash
# Clone repository
git clone <repository-url>
cd Kvalidator

# Build with Maven
mvn clean package

# JAR file được tạo tại:
# target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### Chạy ứng dụng (Run Application)

```bash
# Display help
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help

# Design validation
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -d src/main/resources/examples/design-spec-example.yaml \
  -k ~/.kube/config \
  -o json

# Environment comparison
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -c prod,staging \
  -k config-prod.yaml,config-staging.yaml \
  -o json
```

## Cấu trúc project (Project Structure)

```
Kvalidator/
├── docs/                          # Documentation
│   ├── REQUIREMENTS.md            # Chi tiết requirements
│   ├── USER_GUIDE.md              # Hướng dẫn sử dụng
│   └── ARCHITECTURE.md            # Kiến trúc hệ thống
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/nfv/validator/
│   │   │       ├── KValidatorApplication.java    # Main entry
│   │   │       ├── cli/                          # CLI interface
│   │   │       ├── design/                       # Design validation
│   │   │       ├── comparison/                   # Environment comparison
│   │   │       ├── kubernetes/                   # K8s multi-cluster
│   │   │       ├── config/                       # Configuration
│   │   │       └── model/                        # Data models
│   │   └── resources/
│   │       ├── logback.xml                       # Logging config
│   │       ├── validation-config.yaml            # Validation rules
│   │       └── examples/                         # Example files
│   └── test/                                     # Unit tests
├── pom.xml                        # Maven configuration
└── README.md                      # This file
```

## Tài liệu (Documentation)

Xem thêm documentation chi tiết trong thư mục `docs/`:

- **[REQUIREMENTS.md](docs/REQUIREMENTS.md)**: Requirements và specifications chi tiết
- **[USER_GUIDE.md](docs/USER_GUIDE.md)**: Hướng dẫn sử dụng đầy đủ
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)**: Kiến trúc và design patterns

## Ví dụ sử dụng (Usage Examples)

### Example 1: Validate Production Environment

```bash
java -jar kvalidator.jar \
  --design-validation designs/nfv-prod-design.yaml \
  --kubeconfig ~/.kube/config-production \
  --output json \
  --verbose
```

### Example 2: Compare Multiple Environments

```bash
java -jar kvalidator.jar \
  --compare-environments production,staging,development \
  --kubeconfig configs/prod.yaml,configs/staging.yaml,configs/dev.yaml \
  --output html > comparison-report.html
```

### Example 3: Custom Validation Rules

Edit `src/main/resources/validation-config.yaml` để customize validation rules:

```yaml
rules:
  - id: "NFV-001"
    name: "CPU Resource Limits"
    severity: "HIGH"
    enabled: true
```

## Command Line Options

| Option | Description |
|--------|-------------|
| `-d, --design-validation <file>` | Design specification file |
| `-c, --compare-environments <envs>` | Environments to compare (comma-separated) |
| `-k, --kubeconfig <files>` | Kubeconfig files (comma-separated) |
| `-o, --output <format>` | Output format: json, yaml, html |
| `-v, --verbose` | Enable verbose logging |
| `-h, --help` | Display help |

## Development

### Run tests

```bash
mvn test
```

### Code structure

- Clean architecture với separation of concerns
- Modular design for extensibility
- Comprehensive logging
- Unit test coverage

## Roadmap

### Phase 1 (Current - MVP)
- [x] Project structure setup
- [x] Kubernetes multi-cluster connectivity
- [ ] Basic design validation
- [ ] Environment comparison
- [ ] Report generation (JSON/YAML)

### Phase 2
- [ ] HTML report generation
- [ ] Advanced validation rules
- [ ] Remediation automation
- [ ] Web UI dashboard

### Phase 3
- [ ] Continuous monitoring mode
- [ ] CI/CD integration
- [ ] Historical analysis
- [ ] API server mode

## Contributing

Contributions are welcome! Please read the documentation in `docs/` before contributing.

## License

See [LICENSE](LICENSE) file for details.

## Support

- Check documentation in `docs/` folder
- Review example files in `src/main/resources/examples/`
- Enable verbose mode (`-v`) for detailed logs
- Check logs at `logs/kvalidator.log`

## Contact

For questions and support, please open an issue in the repository.

---

**Built with ❤️ for NFV Infrastructure Teams**
Kubernetes checklist and validator tool
