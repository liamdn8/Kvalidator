# KValidator API Upgrade

## Tổng quan

Phiên bản này nâng cấp KValidator từ command-line tool đơn giản thành một hệ thống web-based với REST API, hỗ trợ async job processing và export kết quả dưới dạng Excel và JSON.

## Các tính năng mới

### 1. REST API Endpoints
- ✅ **POST /api/validate** - Submit validation job
- ✅ **GET /api/validate/{jobId}** - Kiểm tra job status và progress
- ✅ **GET /api/validate/{jobId}/download** - Download Excel report
- ✅ **GET /api/validate/{jobId}/json** - Lấy JSON results cho web UI

### 2. Async Job Processing
- Jobs chạy trong background thread pool
- Real-time progress tracking (percentage, current step)
- Job lifecycle management (PENDING → PROCESSING → COMPLETED/FAILED)

### 3. Dual Export Format
- **Excel** - Giữ nguyên format báo cáo Excel hiện tại
- **JSON** - Thêm export JSON để phục vụ web UI

### 4. Job Storage
- Kết quả lưu tại `/tmp/.kvalidator/results/{jobId}/`
  - `validation-report.xlsx` - Excel report
  - `validation-results.json` - JSON data

## Kiến trúc hệ thống

```
┌─────────────┐
│   Web UI    │ (Frontend - sẽ phát triển)
└──────┬──────┘
       │ HTTP REST API
       ▼
┌─────────────────────────────────────────┐
│   Quarkus REST API Server               │
│   - ValidationResource (API endpoints)  │
│   - ValidationJobService (Job mgmt)     │
│   - AsyncValidationExecutor (Workers)   │
│   - JsonResultExporter (Export)         │
└─────────────────────────────────────────┘
       │
       ├─────── Kubernetes Clients
       │
       └─────── /tmp/.kvalidator/results/
                └── {jobId}/
                    ├── validation-report.xlsx
                    └── validation-results.json
```

## Cấu trúc code mới

### Package Structure
```
com.nfv.validator/
├── api/
│   └── ValidationResource.java          # REST API endpoints
├── model/api/
│   ├── JobStatus.java                   # Job status enum
│   ├── JobProgress.java                 # Progress tracking
│   ├── ValidationJobRequest.java        # API request model
│   ├── ValidationJobResponse.java       # API response model
│   └── ValidationResultJson.java        # JSON export model
└── service/
    ├── ValidationJobService.java        # Job lifecycle management
    ├── AsyncValidationExecutor.java     # Background job executor
    └── JsonResultExporter.java          # JSON export service
```

### Các file mới được tạo
1. **API Layer**
   - `ValidationResource.java` - REST endpoints

2. **Service Layer**
   - `ValidationJobService.java` - Quản lý job state
   - `AsyncValidationExecutor.java` - Async execution
   - `JsonResultExporter.java` - JSON export

3. **Model Layer**
   - `JobStatus.java` - Job status enum
   - `JobProgress.java` - Progress info
   - `ValidationJobRequest.java` - Request model
   - `ValidationJobResponse.java` - Response model
   - `ValidationResultJson.java` - JSON result model

4. **Documentation & Examples**
   - `API_GUIDE.md` - API documentation đầy đủ
   - `demo-api.sh` - Demo script
   - `examples/api-*.json` - Example requests

## Quick Start

### 1. Start API Server
```bash
# Development mode với hot reload
mvn quarkus:dev

# Production mode
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Server sẽ chạy tại: `http://localhost:8080`

### 2. Test với Swagger UI
Mở browser: `http://localhost:8080/swagger-ui`

### 3. Submit validation job
```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "namespaces": ["default", "kube-system"],
    "description": "Compare namespaces"
  }'
```

Response:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "submittedAt": "2026-01-13T10:30:00Z"
}
```

### 4. Check job status
```bash
JOB_ID="550e8400-e29b-41d4-a716-446655440000"
curl http://localhost:8080/api/validate/$JOB_ID | jq
```

### 5. Download results khi completed
```bash
# Download Excel
curl -O -J http://localhost:8080/api/validate/$JOB_ID/download

# Get JSON
curl http://localhost:8080/api/validate/$JOB_ID/json | jq '.summary'
```

### 6. Chạy demo script
```bash
./demo-api.sh
```

## API Flow

### Flow hoàn chỉnh
```
1. Client gửi POST /api/validate
   ↓
2. Server tạo job với status PENDING
   ↓
3. Server trả về 201 với jobId
   ↓
4. Background worker bắt đầu xử lý
   ↓
5. Job status chuyển sang PROCESSING
   ↓
6. Client poll GET /api/validate/{jobId}
   ↓
7. Server trả về progress (0-100%)
   ↓
8. Worker hoàn thành validation
   ↓
9. Export Excel + JSON vào /tmp/.kvalidator/results/{jobId}/
   ↓
10. Job status chuyển sang COMPLETED
    ↓
11. Client download file hoặc get JSON
```

## Configuration

### application.properties
```properties
# HTTP Configuration
quarkus.http.port=8080
quarkus.http.host=0.0.0.0
quarkus.http.cors=true

# Job Storage
kvalidator.results.base-dir=/tmp/.kvalidator/results
kvalidator.job.thread-pool-size=5
kvalidator.job.max-retention-days=7

# OpenAPI
quarkus.swagger-ui.enable=true
quarkus.swagger-ui.path=/swagger-ui
```

## API Examples

### 1. Simple namespace comparison
```json
{
  "namespaces": ["app-dev", "app-prod"]
}
```

### 2. Baseline validation
```json
{
  "namespaces": ["production"],
  "baselinePath": "./baseline-design.yaml",
  "description": "Validate against design spec"
}
```

### 3. Advanced validation
```json
{
  "namespaces": ["cluster1/app-ns", "cluster2/app-ns"],
  "cluster": "cluster1",
  "kinds": ["Deployment", "Service", "ConfigMap"],
  "configFile": "./custom-validation-config.yaml",
  "exportExcel": true
}
```

## Monitoring & Debugging

### Check logs
```bash
tail -f logs/kvalidator.log
```

### Check job results
```bash
ls -la /tmp/.kvalidator/results/
```

### OpenAPI Spec
```bash
curl http://localhost:8080/openapi > openapi.json
```

## Error Handling

API sử dụng standard HTTP status codes:
- `200` - Success
- `201` - Created (job submitted)
- `400` - Bad Request (invalid parameters)
- `404` - Not Found (job không tồn tại)
- `425` - Too Early (job chưa hoàn thành)
- `500` - Internal Server Error

## Migration từ CLI

### Before (CLI mode)
```bash
java -jar kvalidator.jar app-dev app-prod -o report.xlsx
```

### After (API mode)
```bash
# 1. Start server
mvn quarkus:dev &

# 2. Submit job
JOB_ID=$(curl -s -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"namespaces":["app-dev","app-prod"]}' | jq -r '.jobId')

# 3. Wait for completion
while [ "$(curl -s http://localhost:8080/api/validate/$JOB_ID | jq -r '.status')" != "COMPLETED" ]; do
  sleep 2
done

# 4. Download
curl -O -J http://localhost:8080/api/validate/$JOB_ID/download
```

**Note:** CLI mode vẫn hoạt động bình thường!

## Backward Compatibility

✅ **CLI mode vẫn hoạt động như cũ:**
```bash
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar app-dev app-prod
```

✅ **Batch mode vẫn hoạt động:**
```bash
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar -r batch-request.yaml
```

## Roadmap

### Phase 1 (Completed) ✅
- [x] REST API endpoints
- [x] Async job processing
- [x] JSON export
- [x] Progress tracking
- [x] Swagger UI

### Phase 2 (Next)
- [ ] Web UI frontend (React/Vue)
- [ ] WebSocket for real-time updates
- [ ] Job cancellation
- [ ] Redis/Database backend
- [ ] Authentication & Authorization
- [ ] Rate limiting

### Phase 3 (Future)
- [ ] Scheduled validation jobs
- [ ] Email notifications
- [ ] Historical data analysis
- [ ] Multi-tenancy support
- [ ] Custom plugins/extensions

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Test với API
```bash
# Start server
mvn quarkus:dev &

# Run demo
./demo-api.sh

# Manual test
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d @examples/api-validation-request.json
```

## Troubleshooting

### Port already in use
```bash
# Change port in application.properties
quarkus.http.port=8081
```

### Job results not found
```bash
# Check storage directory
ls -la /tmp/.kvalidator/results/

# Create manually if needed
mkdir -p /tmp/.kvalidator/results
```

### CORS issues
```bash
# Enable CORS in application.properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
```

## Production Deployment

### Build native executable (optional)
```bash
mvn package -Pnative
./target/kvalidator-1.0.0-SNAPSHOT-runner
```

### Docker deployment (future)
```bash
docker build -t kvalidator:latest .
docker run -p 8080:8080 -v /tmp/.kvalidator:/tmp/.kvalidator kvalidator:latest
```

### Systemd service
```ini
[Unit]
Description=KValidator API Service
After=network.target

[Service]
Type=simple
User=kvalidator
WorkingDirectory=/opt/kvalidator
ExecStart=/usr/bin/java -jar /opt/kvalidator/kvalidator.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

## Contributing

Khi phát triển thêm features:
1. Tạo branch mới từ `main`
2. Implement feature với tests
3. Update documentation
4. Submit PR

## Support

- GitHub Issues: https://github.com/your-org/kvalidator/issues
- Documentation: [API_GUIDE.md](API_GUIDE.md)
- Swagger UI: http://localhost:8080/swagger-ui

## License

Apache 2.0 - See LICENSE file for details
