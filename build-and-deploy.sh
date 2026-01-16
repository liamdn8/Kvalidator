#!/bin/bash

set -e

echo "🚀 KValidator - Build and Deploy Script"
echo "========================================"

# Configuration
IMAGE_NAME="${IMAGE_NAME:-kvalidator}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
NAMESPACE="${NAMESPACE:-default}"
BUILD_ONLY="${BUILD_ONLY:-false}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Build React frontend
echo -e "\n${BLUE}Step 1: Building React frontend...${NC}"
cd frontend
npm ci
npm run build
cd ..
echo -e "${GREEN}✓ Frontend built successfully${NC}"

# Step 2: Copy frontend to backend resources
echo -e "\n${BLUE}Step 2: Copying frontend to backend resources...${NC}"
rm -rf src/main/resources/META-INF/resources/*
mkdir -p src/main/resources/META-INF/resources/kvalidator/web
cp -r frontend/dist/* src/main/resources/META-INF/resources/kvalidator/web/
echo -e "${GREEN}✓ Frontend copied to backend${NC}"

# Step 3: Build Docker image
echo -e "\n${BLUE}Step 3: Building Docker image...${NC}"
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
echo -e "${GREEN}✓ Docker image built: ${IMAGE_NAME}:${IMAGE_TAG}${NC}"

# Stop here if BUILD_ONLY=true
if [ "$BUILD_ONLY" = "true" ]; then
    echo -e "\n${GREEN}Build completed successfully!${NC}"
    exit 0
fi

# Step 4: Check if Kubernetes is available
echo -e "\n${BLUE}Step 4: Checking Kubernetes cluster...${NC}"
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${YELLOW}Warning: Kubernetes cluster not accessible${NC}"
    echo -e "${YELLOW}Run: export KUBECONFIG=/path/to/your/kubeconfig${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Kubernetes cluster accessible${NC}"

# Step 5: Load image to kind cluster (if using kind)
if kind get clusters 2>/dev/null | grep -q .; then
    CLUSTER_NAME=$(kind get clusters | head -1)
    echo -e "\n${BLUE}Step 5: Loading image to kind cluster: ${CLUSTER_NAME}${NC}"
    kind load docker-image ${IMAGE_NAME}:${IMAGE_TAG} --name ${CLUSTER_NAME}
    echo -e "${GREEN}✓ Image loaded to kind cluster${NC}"
else
    echo -e "\n${YELLOW}Step 5: Skipped (not using kind)${NC}"
fi

# Step 6: Apply Kubernetes manifests
echo -e "\n${BLUE}Step 6: Deploying to Kubernetes...${NC}"
kubectl apply -f k8s/deployment.yaml -n ${NAMESPACE}
echo -e "${GREEN}✓ Deployment applied${NC}"

# Optional: Apply ingress
if [ -f "k8s/ingress.yaml" ]; then
    echo -e "\n${BLUE}Step 7: Applying Ingress...${NC}"
    kubectl apply -f k8s/ingress.yaml -n ${NAMESPACE}
    echo -e "${GREEN}✓ Ingress applied${NC}"
fi

# Step 8: Wait for deployment
echo -e "\n${BLUE}Step 8: Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=300s deployment/kvalidator -n ${NAMESPACE}
echo -e "${GREEN}✓ Deployment ready${NC}"

# Step 9: Show service info
echo -e "\n${BLUE}Step 9: Service Information${NC}"
echo "======================================"
kubectl get svc kvalidator -n ${NAMESPACE}

# Get service URL
SERVICE_TYPE=$(kubectl get svc kvalidator -n ${NAMESPACE} -o jsonpath='{.spec.type}')
if [ "$SERVICE_TYPE" = "LoadBalancer" ]; then
    EXTERNAL_IP=$(kubectl get svc kvalidator -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    if [ -z "$EXTERNAL_IP" ]; then
        EXTERNAL_IP="<pending>"
    fi
    echo -e "\n${GREEN}Access KValidator at: http://${EXTERNAL_IP}${NC}"
elif [ "$SERVICE_TYPE" = "NodePort" ]; then
    NODE_PORT=$(kubectl get svc kvalidator -n ${NAMESPACE} -o jsonpath='{.spec.ports[0].nodePort}')
    NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
    echo -e "\n${GREEN}Access KValidator at: http://${NODE_IP}:${NODE_PORT}${NC}"
fi

# Show pods
echo -e "\n${BLUE}Pods:${NC}"
kubectl get pods -l app=kvalidator -n ${NAMESPACE}

echo -e "\n${GREEN}🎉 Deployment completed successfully!${NC}"
echo -e "\nUseful commands:"
echo -e "  View logs:    kubectl logs -f deployment/kvalidator -n ${NAMESPACE}"
echo -e "  Port forward: kubectl port-forward svc/kvalidator 8080:80 -n ${NAMESPACE}"
echo -e "  Delete:       kubectl delete -f k8s/deployment.yaml -n ${NAMESPACE}"
