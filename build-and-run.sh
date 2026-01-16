#!/bin/bash

# Build and containerize KValidator completely
set -e

echo "🚀 Complete KValidator Build & Deploy"
echo "======================================"
echo ""

# Step 1: Build everything
echo "📦 Building application..."
./build-all.sh

# Step 2: Copy frontend to quarkus-app manually (for fast Docker build)
echo ""
echo "📋 Copying frontend to Quarkus app..."
mkdir -p target/quarkus-app/quarkus/generated-bytecode/resources/META-INF/resources/
cp -r src/main/resources/META-INF/resources/* target/quarkus-app/quarkus/generated-bytecode/resources/META-INF/resources/
echo "✅ Frontend copied to Quarkus runtime"

# Step 3: Build Docker image
echo ""
echo "🐳 Building Docker image..."
docker rm -f kvalidator-test 2>/dev/null || true
docker build -f Dockerfile.fast -t kvalidator:test .
echo "✅ Docker image built: kvalidator:test"

# Step 4: Run with KinD
echo ""
echo "🎯 Running with KinD cluster..."
./test-with-kind.sh

echo ""
echo "🎉 Complete! Access your application:"
echo "   Frontend: http://localhost:8080"
echo "   API: http://localhost:8080/api/validation/namespaces"
echo ""
echo "View logs: docker logs -f kvalidator-test"
