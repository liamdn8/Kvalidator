#!/bin/bash
set -e

echo "🚀 KValidator - Complete Build & Deploy to Docker"
echo "=================================================="
echo ""

# Step 1: Build Frontend
echo "📦 [1/6] Building React Frontend..."
cd frontend
npm run build
cd ..
echo "✅ Frontend built"
echo ""

# Step 2: Copy Frontend to Backend
echo "📋 [2/6] Copying Frontend to Backend Resources..."
rm -rf src/main/resources/META-INF/resources/*
mkdir -p src/main/resources/META-INF/resources/kvalidator/web
cp -r frontend/dist/* src/main/resources/META-INF/resources/kvalidator/web/
echo "✅ Frontend copied to backend"
echo ""

# Step 3: Build Backend
echo "☕ [3/6] Building Java Backend with Maven..."
mvn clean package -DskipTests
echo "✅ Backend built"
echo ""

# Step 4: Copy Frontend to Quarkus App (for runtime)
echo "📦 [4/6] Copying Frontend to Quarkus Runtime..."
mkdir -p target/quarkus-app/quarkus/generated-bytecode/resources/META-INF/resources
cp -r src/main/resources/META-INF/resources/* target/quarkus-app/quarkus/generated-bytecode/resources/META-INF/resources/
echo "✅ Frontend copied to Quarkus app"
echo ""

# Step 5: Build Docker Image
echo "🐳 [5/6] Building Docker Image..."
docker rm -f kvalidator-test 2>/dev/null || true
docker build -f Dockerfile.fast -t kvalidator:test .
echo "✅ Docker image built"
echo ""

# Step 6: Run Container with KinD
echo "🎯 [6/6] Starting Container with KinD..."
./test-with-kind.sh

echo ""
echo "🎉 ============================================"
echo "✅ Build & Deploy Complete!"
echo "🌐 Access: http://localhost:8080"
echo "📊 Logs: docker logs -f kvalidator-test"
echo "🛑 Stop: docker rm -f kvalidator-test"
echo "============================================"
