#!/bin/bash
set -e

# KValidator - One-click build and deploy script

echo "🚀 KValidator - Build and Deploy"
echo "================================="

# Configuration
IMAGE_NAME="kvalidator"
IMAGE_TAG="${IMAGE_TAG:-latest}"
NAMESPACE="${NAMESPACE:-kvalidator}"
REGISTRY="${REGISTRY:-}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

# Step 1: Build Docker image
echo ""
echo "Step 1: Building Docker image..."
if docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .; then
    log_info "Docker image built: ${IMAGE_NAME}:${IMAGE_TAG}"
else
    log_error "Failed to build Docker image"
    exit 1
fi

# Step 2: Push to registry (if specified)
if [ -n "$REGISTRY" ]; then
    echo ""
    echo "Step 2: Pushing to registry..."
    FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${FULL_IMAGE}
    
    if docker push ${FULL_IMAGE}; then
        log_info "Image pushed to registry: ${FULL_IMAGE}"
    else
        log_error "Failed to push image to registry"
        exit 1
    fi
else
    log_warn "No registry specified, skipping push (use REGISTRY=your-registry.io)"
fi

# Step 3: Create namespace
echo ""
echo "Step 3: Creating Kubernetes namespace..."
if kubectl get namespace ${NAMESPACE} &> /dev/null; then
    log_warn "Namespace ${NAMESPACE} already exists"
else
    kubectl create namespace ${NAMESPACE}
    log_info "Namespace created: ${NAMESPACE}"
fi

# Step 4: Apply ConfigMap
echo ""
echo "Step 4: Applying ConfigMap..."
if kubectl apply -f k8s/configmap.yaml -n ${NAMESPACE}; then
    log_info "ConfigMap applied"
else
    log_error "Failed to apply ConfigMap"
    exit 1
fi

# Step 5: Apply Deployment
echo ""
echo "Step 5: Deploying application..."

# Update image in deployment if registry is specified
if [ -n "$REGISTRY" ]; then
    TEMP_DEPLOYMENT=$(mktemp)
    sed "s|image: kvalidator:latest|image: ${FULL_IMAGE}|g" k8s/deployment.yaml > ${TEMP_DEPLOYMENT}
    
    if kubectl apply -f ${TEMP_DEPLOYMENT} -n ${NAMESPACE}; then
        log_info "Deployment applied with registry image"
    else
        log_error "Failed to apply Deployment"
        rm -f ${TEMP_DEPLOYMENT}
        exit 1
    fi
    rm -f ${TEMP_DEPLOYMENT}
else
    if kubectl apply -f k8s/deployment.yaml -n ${NAMESPACE}; then
        log_info "Deployment applied with local image"
    else
        log_error "Failed to apply Deployment"
        exit 1
    fi
fi

# Step 6: Wait for deployment
echo ""
echo "Step 6: Waiting for deployment to be ready..."
if kubectl rollout status deployment/kvalidator -n ${NAMESPACE} --timeout=300s; then
    log_info "Deployment is ready"
else
    log_error "Deployment failed to become ready"
    exit 1
fi

# Step 7: Show status
echo ""
echo "Step 7: Deployment Status"
echo "========================"
kubectl get pods -n ${NAMESPACE} -l app=kvalidator
echo ""
kubectl get svc -n ${NAMESPACE} kvalidator

# Step 8: Access information
echo ""
echo "🎉 Deployment Complete!"
echo "======================="
echo ""
echo "Access the application:"
echo ""

SERVICE_TYPE=$(kubectl get svc kvalidator -n ${NAMESPACE} -o jsonpath='{.spec.type}')

if [ "$SERVICE_TYPE" == "LoadBalancer" ]; then
    echo "1. Wait for LoadBalancer external IP:"
    echo "   kubectl get svc kvalidator -n ${NAMESPACE} -w"
    echo ""
    echo "2. Access via LoadBalancer IP:"
    echo "   http://<EXTERNAL-IP>"
else
    echo "Port-forward to local machine:"
    echo "   kubectl port-forward svc/kvalidator 8080:80 -n ${NAMESPACE}"
    echo ""
    echo "Then open: http://localhost:8080"
fi

echo ""
echo "View logs:"
echo "   kubectl logs -f deployment/kvalidator -n ${NAMESPACE}"
echo ""
echo "Delete deployment:"
echo "   kubectl delete namespace ${NAMESPACE}"
echo ""
