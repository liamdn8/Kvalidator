# KValidator - Requirements & Specifications

## Tổng quan dự án (Project Overview)

KValidator là công cụ tự động kiểm tra và validate các cấu hình trên môi trường ảo hóa và triển khai cloud cho viễn thông (NFV Infrastructure).

## Yêu cầu chức năng (Functional Requirements)

### 1. Đối chiếu thiết kế hệ thống so với thực tế triển khai

**Mô tả**: So sánh cấu hình thực tế trên môi trường Kubernetes với thiết kế ban đầu

**Yêu cầu chi tiết**:
- [ ] Đọc file thiết kế (YAML/JSON format)
- [ ] Kết nối tới Kubernetes cluster để lấy cấu hình thực tế
- [ ] So sánh các thông số:
  - Số lượng nodes và cấu hình
  - Resource requests/limits của workloads
  - Network policies và cấu hình mạng
  - Storage classes và persistent volumes
  - High availability settings (replica count, pod disruption budgets)
  - Security policies
- [ ] Phát hiện sự khác biệt và thiếu sót
- [ ] Tạo báo cáo chi tiết với mức độ nghiêm trọng (Critical, High, Medium, Low)

**Input**: 
- Design specification file (YAML)
- Kubeconfig file để kết nối cluster

**Output**:
- Validation report (JSON/YAML/HTML)
- Danh sách issues với remediation suggestions

### 2. Đối chiếu, so sánh các môi trường với nhau

**Mô tả**: So sánh cấu hình giữa nhiều môi trường (Production, Staging, Development)

**Yêu cầu chi tiết**:
- [ ] Kết nối đồng thời tới nhiều Kubernetes clusters
- [ ] Thu thập cấu hình từ mỗi cluster
- [ ] So sánh các thông số giống và khác nhau:
  - Deployments và configuration
  - Services và networking
  - ConfigMaps và Secrets (metadata only)
  - Resource quotas
  - RBAC policies
- [ ] Phát hiện drift giữa các môi trường
- [ ] Highlight các differences có thể gây vấn đề

**Input**:
- Multiple kubeconfig files
- List of environments to compare
- (Optional) Configuration items to focus on

**Output**:
- Comparison matrix
- Difference report với severity levels
- Recommendations for standardization

## Yêu cầu kỹ thuật (Technical Requirements)

### Ngôn ngữ và Framework
- **Language**: Java 11
- **Build Tool**: Maven 3.x
- **Kubernetes Client**: Fabric8 Kubernetes Client
- **Configuration**: YAML/JSON processing (Jackson, SnakeYAML)
- **Logging**: SLF4J + Logback
- **Testing**: JUnit 5, Mockito

### Khả năng kết nối Multi-cluster
- Hỗ trợ kết nối đồng thời tới nhiều Kubernetes clusters
- Hỗ trợ các phương thức authentication:
  - Kubeconfig file
  - Service Account Token
  - Client certificates
- Connection pooling và timeout handling
- Retry mechanism cho network failures

### Hiệu năng (Performance)
- Parallel processing khi query nhiều clusters
- Caching cho repeated queries
- Timeout configurable cho mỗi operation
- Memory efficient cho large clusters

### Bảo mật (Security)
- Không log sensitive data (tokens, passwords)
- Secure storage cho credentials
- TLS verification cho cluster connections
- Read-only operations (không modify cluster state)

## Yêu cầu phi chức năng (Non-functional Requirements)

### Usability
- Command-line interface đơn giản, dễ sử dụng
- Verbose mode cho debugging
- Multiple output formats (JSON, YAML, HTML)
- Progress indicators cho long-running operations

### Maintainability
- Clean code architecture
- Comprehensive documentation
- Unit tests coverage > 70%
- Logging ở các level phù hợp

### Portability
- Cross-platform (Linux, macOS, Windows)
- Containerizable (Docker support)
- No OS-specific dependencies

## Roadmap

### Phase 1 (Current - MVP)
- [x] Project structure setup
- [ ] Kubernetes multi-cluster connectivity
- [ ] Basic design validation
- [ ] Environment comparison
- [ ] Report generation (JSON/YAML)

### Phase 2
- [ ] Advanced validation rules
- [ ] HTML report generation
- [ ] Configuration drift detection
- [ ] Remediation automation
- [ ] Web UI dashboard

### Phase 3
- [ ] Continuous monitoring mode
- [ ] Integration with CI/CD pipelines
- [ ] Historical trend analysis
- [ ] API server mode
- [ ] Plugin system for custom validators

## Validation Rules Examples

### NFV-specific Rules
1. **CPU Pinning**: Verify CPU pinning configuration for VNFs
2. **NUMA Alignment**: Check NUMA node alignment
3. **Huge Pages**: Validate huge pages allocation
4. **SR-IOV**: Verify SR-IOV network interfaces
5. **Network Multi-homing**: Check multiple network attachments
6. **QoS Policies**: Validate QoS configurations
7. **Licensing**: Check license server connectivity and allocations

### Infrastructure Rules
1. **High Availability**: Replica counts, pod disruption budgets
2. **Resource Management**: Requests/limits, resource quotas
3. **Security**: Network policies, pod security policies, RBAC
4. **Storage**: Persistent volume claims, storage classes
5. **Monitoring**: Prometheus exporters, ServiceMonitors
6. **Backup**: Velero backup schedules

## References

### Standards
- ETSI NFV Documentation
- 3GPP specifications
- Kubernetes Best Practices
- Cloud Native Computing Foundation (CNCF) guidelines

### Related Tools
- Kubernetes kubeval
- Polaris
- kube-bench
- Sonobuoy
