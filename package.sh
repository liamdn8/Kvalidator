#!/bin/bash

# Package script - Create release package for KValidator
set -e

VERSION="1.0.0"
PACKAGE_NAME="kvalidator-${VERSION}"
PACKAGE_DIR="${PACKAGE_NAME}"

echo "📦 KValidator Release Packager v${VERSION}"
echo "=========================================="
echo ""

# Step 1: Build everything
echo "🔧 Step 1/4: Building application..."
./build-all.sh
echo ""

# Step 2: Create package directory
echo "📁 Step 2/4: Creating package directory..."
rm -rf "${PACKAGE_DIR}" "${PACKAGE_DIR}.tar.gz"
mkdir -p "${PACKAGE_DIR}"
echo "✅ Created: ${PACKAGE_DIR}/"
echo ""

# Step 3: Copy artifacts
echo "📋 Step 3/4: Copying artifacts..."

# Copy Quarkus application
cp -r target/quarkus-app "${PACKAGE_DIR}/"
echo "✅ Copied: quarkus-app/"

# Copy Docker files
cp Dockerfile "${PACKAGE_DIR}/"
cp Dockerfile.fast "${PACKAGE_DIR}/"
echo "✅ Copied: Dockerfiles"

# Copy configuration
cp src/main/resources/validation-config.yaml "${PACKAGE_DIR}/"
echo "✅ Copied: validation-config.yaml"

# Copy documentation
cp README.md "${PACKAGE_DIR}/"
cp LICENSE "${PACKAGE_DIR}/"
cp QUICKSTART.md "${PACKAGE_DIR}/" 2>/dev/null || echo "⚠️  QUICKSTART.md not found, skipping"
cp USAGE.md "${PACKAGE_DIR}/" 2>/dev/null || echo "⚠️  USAGE.md not found, skipping"
echo "✅ Copied: Documentation"

# Create run script
cat > "${PACKAGE_DIR}/run.sh" << 'EOF'
#!/bin/bash

echo "🚀 Starting KValidator..."
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java 11+ is required but not found!"
    echo "   Please install Java 11 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "❌ Java 11+ is required, but found version $JAVA_VERSION"
    exit 1
fi

# Run the application
java -jar quarkus-app/quarkus-run.jar

EOF

chmod +x "${PACKAGE_DIR}/run.sh"
echo "✅ Created: run.sh"

# Create Windows batch file
cat > "${PACKAGE_DIR}/run.cmd" << 'EOF'
@echo off
echo Starting KValidator...
echo.

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java 11+ is required but not found!
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

REM Run the application
java -jar quarkus-app\quarkus-run.jar

EOF

echo "✅ Created: run.cmd"

# Create install guide
cat > "${PACKAGE_DIR}/INSTALL.md" << 'EOF'
# KValidator Installation Guide

## Requirements

- Java 11 or higher
- Access to Kubernetes cluster(s)
- kubectl configured with cluster credentials

## Installation Steps

### Option 1: Run Directly (Recommended for Testing)

1. Extract the package:
   ```bash
   tar -xzf kvalidator-1.0.0.tar.gz
   cd kvalidator-1.0.0
   ```

2. Run the application:
   - Linux/Mac: `./run.sh`
   - Windows: `run.cmd`

3. Access the web UI:
   - Open browser to: http://localhost:8080

### Option 2: Docker (Recommended for Production)

1. Build the Docker image:
   ```bash
   docker build -f Dockerfile.fast -t kvalidator:1.0.0 .
   ```

2. Run the container:
   ```bash
   docker run -p 8080:8080 \
     -v ~/.kube:/home/kvalidator/.kube:ro \
     kvalidator:1.0.0
   ```

3. Access the web UI:
   - Open browser to: http://localhost:8080

### Option 3: Kubernetes Deployment

See QUICKSTART.md for Kubernetes deployment instructions.

## Configuration

Edit `validation-config.yaml` to customize validation rules.

## Troubleshooting

### Port 8080 already in use

Change the port by setting the environment variable:
```bash
QUARKUS_HTTP_PORT=9090 java -jar quarkus-app/quarkus-run.jar
```

### Cannot connect to Kubernetes cluster

Ensure kubectl is configured correctly:
```bash
kubectl cluster-info
```

## Getting Help

- Documentation: See README.md and USAGE.md
- Issues: https://github.com/yourusername/kvalidator/issues

EOF

echo "✅ Created: INSTALL.md"
echo ""

# Step 4: Create tarball
echo "📦 Step 4/4: Creating release package..."
tar -czf "${PACKAGE_DIR}.tar.gz" "${PACKAGE_DIR}"
PACKAGE_SIZE=$(du -h "${PACKAGE_DIR}.tar.gz" | cut -f1)
echo "✅ Created: ${PACKAGE_DIR}.tar.gz (${PACKAGE_SIZE})"
echo ""

# Summary
echo "🎉 Package created successfully!"
echo ""
echo "📊 Package Contents:"
echo "   - Application: quarkus-app/"
echo "   - Web UI: Embedded in application"
echo "   - Configuration: validation-config.yaml"
echo "   - Dockerfiles: Dockerfile, Dockerfile.fast"
echo "   - Scripts: run.sh, run.cmd"
echo "   - Documentation: README.md, INSTALL.md, etc."
echo ""
echo "📦 Release Package: ${PACKAGE_DIR}.tar.gz (${PACKAGE_SIZE})"
echo ""
echo "🚀 To test:"
echo "   tar -xzf ${PACKAGE_DIR}.tar.gz"
echo "   cd ${PACKAGE_DIR}"
echo "   ./run.sh"
echo ""
echo "🌐 Then open: http://localhost:8080"
echo ""
