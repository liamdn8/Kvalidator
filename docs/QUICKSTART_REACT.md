# Quick Start Guide - Kvalidator React Frontend

## Setup trong 3 bước

### 1. Start Backend (Terminal 1)
```bash
cd /home/liamdn/Kvalidator
mvn quarkus:dev
```
- Quarkus API server sẽ chạy ở **http://localhost:8080**
- Đợi đến khi thấy "Listening on: http://0.0.0.0:8080"

### 2. Start Frontend (Terminal 2)
```bash
cd /home/liamdn/Kvalidator/frontend
npm run dev
```
- React app sẽ chạy ở **http://localhost:3000**
- Tự động mở browser hoặc truy cập thủ công

### 3. Sử dụng Web UI

#### Truy cập
```
http://localhost:3000
```

#### Workflow Validation

**Option 1: YAML Baseline**
1. Chọn "YAML Files" trong Baseline Configuration
2. Paste YAML content vào textbox
3. Trong Target Selection:
   - Search namespace hoặc
   - Select cluster → namespace → Add
4. Click "Start Validation"
5. Xem results và export Excel nếu cần

**Option 2: Namespace Baseline**
1. Chọn "Cluster Namespace" trong Baseline Configuration
2. Select cluster → auto-load namespaces → select namespace
3. Add targets để compare
4. Click "Start Validation"

## Kiểm tra Services

### Backend API
```bash
# Check clusters
curl http://localhost:8080/kvalidator/api/kubernetes/clusters

# Check namespaces
curl "http://localhost:8080/kvalidator/api/kubernetes/namespaces?cluster=docker-desktop"

# Search
curl "http://localhost:8080/kvalidator/api/kubernetes/namespaces/search?keyword=kube"
```

### Frontend
```bash
# Check React app
curl http://localhost:3000

# Should return HTML with "Kvalidator" title
```

## Architecture

```
┌─────────────────────────────────────────┐
│         Browser (localhost:3000)        │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │   React App (Vite Dev Server)    │ │
│  │                                   │ │
│  │  - Components (Ant Design)       │ │
│  │  - Lucide Icons                  │ │
│  │  - Axios HTTP Client             │ │
│  └───────────┬───────────────────────┘ │
│              │ Proxy /api/*            │
└──────────────┼─────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    Quarkus Backend (localhost:8080)     │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │   REST API Endpoints              │ │
│  │                                   │ │
│  │  GET  /kvalidator/api/kubernetes/clusters    │ │
│  │  GET  /kvalidator/api/kubernetes/namespaces  │ │
│  │  GET  /kvalidator/api/kubernetes/.../search  │ │
│  │  POST /kvalidator/api/validate               │ │
│  └───────────┬───────────────────────┘ │
│              │                         │
│              ▼                         │
│  ┌───────────────────────────────────┐ │
│  │   Kubernetes Java Client          │ │
│  │   (Fabric8)                       │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

## Features Checklist

- ✅ Auto-load clusters from API
- ✅ Cascade loading: cluster → namespaces
- ✅ Search namespaces across all clusters
- ✅ YAML baseline support
- ✅ Namespace baseline support
- ✅ Multiple target selection
- ✅ Duplicate detection
- ✅ Real-time validation
- ✅ Results with statistics
- ✅ Color-coded status badges
- ✅ Excel export
- ✅ Loading states
- ✅ Error handling with toasts
- ✅ Responsive design

## Troubleshooting

### Frontend không kết nối được API
**Triệu chứng**: API calls fail với CORS errors hoặc connection refused

**Giải pháp**:
1. Kiểm tra Quarkus đang chạy:
   ```bash
   curl http://localhost:8080/api/kubernetes/clusters
   ```
2. Check CORS trong `application.properties`:
   ```properties
   quarkus.http.cors=true
   quarkus.http.cors.origins=*
   ```
3. Restart cả frontend và backend

### Port 3000 hoặc 8080 bị chiếm
**Giải pháp**:
```bash
# Kill process trên port 3000
lsof -ti:3000 | xargs kill -9

# Kill process trên port 8080
lsof -ti:8080 | xargs kill -9
```

### Icons không hiển thị
**Giải pháp**:
```bash
cd frontend
npm install lucide-react --save
```

### Build errors
**Giải pháp**:
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
npm run dev
```

## Production Deployment

### Build Frontend
```bash
cd frontend
npm run build
```

### Option 1: Deploy to Quarkus
```bash
# Copy build to Quarkus resources
cp -r dist/* ../src/main/resources/META-INF/resources/ui/

# Build Quarkus with frontend included
cd ..
mvn clean package -DskipTests
```

### Option 2: Separate Deployment
- Frontend: Deploy `dist/` to nginx/Apache/CDN
- Backend: Deploy Quarkus JAR to server
- Update API URL in frontend code

## Development Tips

### Hot Reload
- Frontend: Vite auto-reloads on file changes
- Backend: Quarkus auto-recompiles on save

### DevTools
- Open browser DevTools (F12)
- Network tab: Inspect API calls
- Console: Check errors and logs

### API Testing
Use Quarkus Swagger UI:
```
http://localhost:8080/swagger-ui
```

### Component Development
- Edit components in `src/components/`
- Changes reflect immediately
- Check console for errors

## Performance

### Development
- Vite HMR: ~50ms updates
- Initial load: ~1-2 seconds

### Production
- Build time: ~10-20 seconds
- Bundle size: ~500KB (gzipped)
- Load time: <1 second

## Next Steps

1. **Customize UI**: Edit components, colors, layout
2. **Add features**: File upload, dark mode, history
3. **Optimize**: Code splitting, lazy loading
4. **Testing**: Add Jest, React Testing Library
5. **CI/CD**: Automate build and deployment

## Support

- Frontend docs: [frontend/README.md](README.md)
- Backend API: http://localhost:8080/swagger-ui
- Logs: Check browser console & terminal output
