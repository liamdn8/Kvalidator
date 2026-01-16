# KValidator Kubernetes Deployment

## Quick Start

### 1. Build and Deploy (Automatic)

```bash
# Build Docker image and deploy to Kubernetes
chmod +x build-and-deploy.sh
./build-and-deploy.sh
```

### 2. Build Only (No Deploy)

```bash
# Just build the Docker image
BUILD_ONLY=true ./build-and-deploy.sh
```

### 3. Custom Configuration

```bash
# Custom image name and tag
IMAGE_NAME=myregistry/kvalidator IMAGE_TAG=v1.0.0 ./build-and-deploy.sh

# Deploy to specific namespace
NAMESPACE=kvalidator-prod ./build-and-deploy.sh
```

## Manual Deployment

### Step 1: Build Docker Image

```bash
# Build frontend
cd frontend
npm ci
npm run build
cd ..

# Copy to backend
cp -r frontend/dist/* src/main/resources/META-INF/resources/

# Build Docker image
docker build -t kvalidator:latest .
```

### Step 2: Load to Kind (if using kind)

```bash
kind load docker-image kvalidator:latest --name <cluster-name>
```

### Step 3: Deploy to Kubernetes

```bash
# Apply all manifests
kubectl apply -f k8s/deployment.yaml

# Optional: Apply ingress
kubectl apply -f k8s/ingress.yaml
```

### Step 4: Access Application

```bash
# Port forward
kubectl port-forward svc/kvalidator 8080:80

# Access at: http://localhost:8080
```

## Configuration

### Environment Variables

- `JAVA_OPTS`: JVM options (default: `-Xmx512m -Xms256m`)
- `QUARKUS_HTTP_PORT`: HTTP port (default: `8080`)
- `QUARKUS_HTTP_HOST`: Bind host (default: `0.0.0.0`)

### Resources

- **Requests**: 512Mi memory, 500m CPU
- **Limits**: 1Gi memory, 1000m CPU

### Service Account

The deployment includes a ServiceAccount with ClusterRole permissions to:
- List and get namespaces, pods, services, configmaps, secrets
- List and get deployments, statefulsets, daemonsets

### Kubeconfig (Optional)

To access external clusters, create a secret:

```bash
kubectl create secret generic kvalidator-kubeconfig \
  --from-file=config=/path/to/external/kubeconfig
```

## Monitoring

### View Logs

```bash
kubectl logs -f deployment/kvalidator
```

### Health Checks

```bash
# Liveness
curl http://<service-ip>:8080/q/health/live

# Readiness
curl http://<service-ip>:8080/q/health/ready
```

### Pod Status

```bash
kubectl get pods -l app=kvalidator
kubectl describe pod <pod-name>
```

## Troubleshooting

### Pod Not Starting

```bash
# Check events
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>

# Check resources
kubectl top pod <pod-name>
```

### Service Not Accessible

```bash
# Check service
kubectl get svc kvalidator
kubectl describe svc kvalidator

# Check endpoints
kubectl get endpoints kvalidator

# Test from within cluster
kubectl run -it --rm debug --image=busybox --restart=Never -- wget -O- http://kvalidator:80
```

### Image Pull Issues

```bash
# For kind clusters
kind load docker-image kvalidator:latest --name <cluster-name>

# For private registries, create image pull secret
kubectl create secret docker-registry regcred \
  --docker-server=<registry> \
  --docker-username=<username> \
  --docker-password=<password>
```

## Cleanup

```bash
# Delete deployment and service
kubectl delete -f k8s/deployment.yaml

# Delete ingress (if applied)
kubectl delete -f k8s/ingress.yaml

# Delete image from kind (if using kind)
docker exec <kind-node> crictl rmi kvalidator:latest
```

## Production Considerations

1. **Use specific image tags** instead of `:latest`
2. **Configure resource limits** based on workload
3. **Set up persistent storage** for validation results
4. **Enable TLS/SSL** for ingress
5. **Configure horizontal pod autoscaling** if needed
6. **Set up monitoring** with Prometheus/Grafana
7. **Configure backup** for validation reports
