#!/bin/bash

# Build and deploy React UI to Quarkus

echo "================================="
echo "Building React UI for Kvalidator"
echo "================================="

# Navigate to frontend directory
cd frontend || exit 1

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
  echo "Installing npm dependencies..."
  npm install
fi

# Build React app
echo ""
echo "Building React application..."
npm run build

if [ $? -ne 0 ]; then
  echo "ERROR: React build failed!"
  exit 1
fi

# Copy to Quarkus resources
echo ""
echo "Copying build to Quarkus resources..."
# node copy-to-quarkus.js (Use shell commands instead as script is missing)
rm -rf ../src/main/resources/META-INF/resources/*
mkdir -p ../src/main/resources/META-INF/resources/kvalidator/web
cp -r dist/* ../src/main/resources/META-INF/resources/kvalidator/web/

if [ $? -ne 0 ]; then
  echo "ERROR: Copy to Quarkus failed!"
  exit 1
fi

# Return to project root
cd ..

# Build Java application
echo ""
echo "Building Java application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
  echo "ERROR: Maven build failed!"
  exit 1
fi

echo ""
echo "================================="
echo "Build completed successfully!"
echo "================================="
echo ""
echo "Next steps:"
echo "1. Run the application: java -jar target/quarkus-app/quarkus-run.jar"
echo "2. Open http://localhost:8080/ui in your browser"
echo ""
