# KValidator - Kubernetes Deployment Guide

## Build Docker Image

```bash
# Build the Docker image (includes both React frontend and Java backend)
docker build -t kvalidator:latest .

# Or with version tag
docker build -t kvalidator:1.0.0 .

# Push to registry (optional)
docker tag kvalidator:latest your-registry/kvalidator:latest
docker push your-registry/kvalidator:latest
```

## Deploy to Kubernetes

### 1. Create Namespace

```bash
kubectl create namespace kvalidator
```

### 2. Deploy ConfigMap (validation-config.yaml)

```bash
kubectl apply -f k8s/configmap.yaml -n kvalidator
```

### 3. Deploy Application

```bash
kubectl apply -f k8s/deployment.yaml -n kvalidator
```

### 4. Verify Deployment

```bash
# Check pod status
kubectl get pods -n kvalidator

# Check service
kubectl get svc -n kvalidator

# Check logs
kubectl logs -f deployment/kvalidator -n kvalidator
```

### 5. Access Application

```bash
# If using LoadBalancer
kubectl get svc kvalidator -n kvalidator

# If using port-forward
kubectl port-forward svc/kvalidator 8080:80 -n kvalidator
```

Then open browser: http://localhost:8080

## Configuration

### Update Validation Config

Edit the ConfigMap and restart pods:

```bash
# Edit config
kubectl edit configmap kvalidator-config -n kvalidator

# Restart deployment
kubectl rollout restart deployment/kvalidator -n kvalidator
```

### Configure Kubernetes Access

The application uses ServiceAccount for in-cluster access. For multi-cluster support:

```bash
# Create secret with kubeconfig
kubectl create secret generic kvalidator-kubeconfig \
  --from-file=config=/path/to/your/kubeconfig \
  -n kvalidator
```

## Architecture

```
┌─────────────────────────────────────────┐
│          Docker Container               │
│  ┌────────────────────────────────┐    │
│  │   React Frontend (Port 8080)   │    │
│  │   - Built static files         │    │
│  │   - Served by Quarkus          │    │
│  └────────────────────────────────┘    │
│  ┌────────────────────────────────┐    │
│  │   Quarkus Backend (Port 8080)  │    │
│  │   - REST API                   │    │
│  │   - Kubernetes Client          │    │
│  │   - Validation Engine          │    │
│  │   - Excel Export               │    │
│  └────────────────────────────────┘    │
│  ┌────────────────────────────────┐    │
│  │   ConfigMap Volume             │    │
│  │   /app/config/                 │    │
│  │   - validation-config.yaml     │    │
│  └────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

## Resource Requirements

- **Memory**: 512Mi request, 1Gi limit
- **CPU**: 500m request, 1000m limit
- **Storage**: EmptyDir for temporary results

## Health Checks

- **Liveness**: `/q/health/live` - Checks if application is running
- **Readiness**: `/q/health/ready` - Checks if application is ready to serve traffic

## Troubleshooting

### Pod not starting

```bash
kubectl describe pod -l app=kvalidator -n kvalidator
kubectl logs -l app=kvalidator -n kvalidator
```

### Permission issues

Check ServiceAccount and RBAC:

```bash
kubectl get sa kvalidator -n kvalidator
kubectl get clusterrole kvalidator-role
kubectl get clusterrolebinding kvalidator-binding
```

### Config not loading

Verify ConfigMap:

```bash
kubectl get configmap kvalidator-config -n kvalidator -o yaml
```

## Scaling

```bash
# Scale replicas
kubectl scale deployment kvalidator --replicas=3 -n kvalidator

# Autoscaling
kubectl autoscale deployment kvalidator \
  --cpu-percent=70 \
  --min=1 \
  --max=5 \
  -n kvalidator
```

## Cleanup

```bash
kubectl delete namespace kvalidator
```
