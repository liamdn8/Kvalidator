#!/bin/bash

# Build script for KValidator - Build everything before containerizing
set -e

echo "🔧 KValidator Build Script"
echo "=========================="
echo ""

# Step 1: Build Frontend
# echo "📦 Step 1/3: Building React Frontend..."
cd frontend
npm ci
npm run build
cd ..
# echo "✅ Frontend built to: frontend/dist/"
# echo ""

# Step 2: Copy Frontend to Backend Resources
echo "📋 Step 2/3: Copying Frontend to Backend..."
rm -rf src/main/resources/META-INF/resources/kvalidator/web
mkdir -p src/main/resources/META-INF/resources/kvalidator/web
cp -r frontend/dist/* src/main/resources/META-INF/resources/kvalidator/web/
echo "✅ Frontend copied to: src/main/resources/META-INF/resources/kvalidator/web/"
echo ""

# Step 3: Build Backend with Maven
echo "☕ Step 3/3: Building Java Backend..."
mvn clean package -DskipTests
echo "✅ Backend built to: target/quarkus-app/"
echo ""

# Verify artifacts
echo "🔍 Verifying build artifacts..."
if [ -f "target/quarkus-app/quarkus-run.jar" ]; then
    echo "✅ Backend JAR: $(ls -lh target/quarkus-app/quarkus-run.jar | awk '{print $5}')"
else
    echo "❌ Backend JAR not found!"
    exit 1
fi

if [ -f "target/quarkus-app/quarkus-app-dependencies.txt" ]; then
    echo "✅ Backend app structure verified"
else
    echo "❌ Backend app structure missing!"
    exit 1
fi

# Check if frontend was included in the build
if [ -f "target/classes/META-INF/resources/kvalidator/web/index.html" ]; then
    echo "✅ Frontend index.html found in build"
else
    echo "❌ Frontend not included in build!"
    echo "   Checking: target/classes/META-INF/resources/kvalidator/web/"
    ls -la target/classes/META-INF/resources/kvalidator/web/ 2>/dev/null || echo "   Directory does not exist!"
    exit 1
fi

echo ""
echo "🎉 Build complete! Ready to containerize:"
echo "   docker build -f Dockerfile.fast -t kvalidator:test ."
echo ""
echo "Or run locally:"
echo "   java -jar target/quarkus-app/quarkus-run.jar"
