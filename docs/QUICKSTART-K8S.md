# KValidator - Quick Start Guide

## 🚀 One-Command Deploy

```bash
./deploy.sh
```

Hoặc với custom registry:

```bash
REGISTRY=your-registry.io IMAGE_TAG=1.0.0 ./deploy.sh
```

## 📦 What's Included

Một Docker container duy nhất chứa:

✅ **React Frontend** (Vite build) - Served tại port 8080
✅ **Quarkus Backend** (Java) - REST API tại port 8080  
✅ **validation-config.yaml** - Mounted từ ConfigMap
✅ **Kubernetes Client** - Tự động kết nối cluster
✅ **Health Checks** - Liveness & Readiness probes

## 🔧 Configuration

### File cấu hình được đóng gói:

1. **Built-in** (trong JAR):
   - `src/main/resources/validation-config.yaml`
   - Được copy vào `/app/config/validation-config.yaml`

2. **ConfigMap** (có thể override):
   - `k8s/configmap.yaml`
   - Mount vào `/app/config/validation-config.yaml`
   - Ưu tiên cao hơn built-in config

### Cập nhật config trong Kubernetes:

```bash
# Edit ConfigMap
kubectl edit configmap kvalidator-config -n kvalidator

# Restart để apply changes
kubectl rollout restart deployment/kvalidator -n kvalidator
```

## 🌐 Truy cập ứng dụng

### Option 1: LoadBalancer (Production)

```bash
# Lấy External IP
kubectl get svc kvalidator -n kvalidator

# Access
http://<EXTERNAL-IP>
```

### Option 2: Port Forward (Development)

```bash
kubectl port-forward svc/kvalidator 8080:80 -n kvalidator
```

Mở browser: http://localhost:8080

## 🔍 Kiểm tra

```bash
# Pod status
kubectl get pods -n kvalidator

# Logs
kubectl logs -f deployment/kvalidator -n kvalidator

# Health check
kubectl get pods -n kvalidator -o wide
curl http://<POD-IP>:8080/q/health
```

## 🎯 Workflow

1. **Build**: `docker build -t kvalidator:latest .`
   - Stage 1: Build React frontend
   - Stage 2: Build Java backend + embed frontend
   - Stage 3: Runtime image với cả 2

2. **Deploy**: `kubectl apply -f k8s/`
   - ConfigMap: validation-config.yaml
   - Deployment: kvalidator pod
   - Service: LoadBalancer/NodePort
   - ServiceAccount + RBAC

3. **Access**: Frontend tự động gọi backend API
   - Frontend: http://localhost:8080/
   - API: http://localhost:8080/api/validation/*
   - Health: http://localhost:8080/q/health

## 📊 Architecture

```
┌─────────────────────────────────────┐
│   Browser                           │
│   http://localhost:8080             │
└────────────┬────────────────────────┘
             │
             v
┌─────────────────────────────────────┐
│   Kubernetes Service                │
│   LoadBalancer / NodePort           │
│   Port: 80 → 8080                   │
└────────────┬────────────────────────┘
             │
             v
┌─────────────────────────────────────┐
│   KValidator Pod                    │
├─────────────────────────────────────┤
│  React App (Static Files)           │
│  - Built with Vite                  │
│  - Served by Quarkus                │
│  - Single Page App                  │
├─────────────────────────────────────┤
│  Quarkus Backend (Port 8080)        │
│  - REST API Endpoints               │
│  - Kubernetes Client                │
│  - Validation Engine                │
│  - Excel Export                     │
├─────────────────────────────────────┤
│  ConfigMap Volume                   │
│  /app/config/validation-config.yaml │
└─────────────────────────────────────┘
             │
             v
┌─────────────────────────────────────┐
│   Kubernetes API                    │
│   (via ServiceAccount)              │
└─────────────────────────────────────┘
```

## 🛠️ Customization

### Thay đổi resources:

Edit `k8s/deployment.yaml`:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### Thay đổi replicas:

```bash
kubectl scale deployment kvalidator --replicas=3 -n kvalidator
```

### Enable autoscaling:

```bash
kubectl autoscale deployment kvalidator \
  --cpu-percent=70 \
  --min=1 \
  --max=5 \
  -n kvalidator
```

## 🧹 Cleanup

```bash
# Xóa toàn bộ
kubectl delete namespace kvalidator

# Xóa local image
docker rmi kvalidator:latest
```

## 📝 Environment Variables

Tất cả đã được cấu hình tự động trong deployment:

- `QUARKUS_HTTP_HOST=0.0.0.0` - Listen on all interfaces
- `QUARKUS_HTTP_PORT=8080` - Port number
- `JAVA_OPTS=-Xmx512m -Xms256m` - JVM settings

## 🔐 Security

- ServiceAccount với RBAC permissions
- ReadOnly ConfigMap mount
- Health checks enabled
- Resource limits enforced
- Optional kubeconfig secret support

## ❓ Troubleshooting

### Pod không start

```bash
kubectl describe pod -l app=kvalidator -n kvalidator
kubectl logs -l app=kvalidator -n kvalidator --tail=100
```

### Config không load

```bash
# Check ConfigMap
kubectl get cm kvalidator-config -n kvalidator -o yaml

# Check mount trong pod
kubectl exec -it deployment/kvalidator -n kvalidator -- ls -la /app/config/
```

### Không kết nối được Kubernetes

```bash
# Check RBAC
kubectl get sa kvalidator -n kvalidator
kubectl auth can-i list namespaces --as=system:serviceaccount:kvalidator:kvalidator
```
