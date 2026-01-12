#!/bin/bash

# Package script for KValidator release

VERSION="1.0.0-SNAPSHOT"
RELEASE_DIR="kvalidator-release"

echo "📦 Packaging KValidator $VERSION..."

# Clean and build
echo "Building JAR..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

# Create release directory
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

# Copy files
echo "Copying files..."
cp target/kvalidator-$VERSION-jar-with-dependencies.jar "$RELEASE_DIR/kvalidator.jar"
cp src/main/resources/validation-config.yaml "$RELEASE_DIR/"
cp baseline-design.yaml "$RELEASE_DIR/baseline-example.yaml"
cp README.md "$RELEASE_DIR/"
cp USAGE.md "$RELEASE_DIR/"
cp QUICKSTART.md "$RELEASE_DIR/"
cp LICENSE "$RELEASE_DIR/"

# Create run scripts
cat > "$RELEASE_DIR/run.sh" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/kvalidator.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    exit 1
fi

if [ ! -f "./validation-config.yaml" ]; then
    echo "⚠️  validation-config.yaml not found, copying default..."
    cp "$SCRIPT_DIR/validation-config.yaml" .
fi

java -jar "$JAR_FILE" "$@"
EOF

cat > "$RELEASE_DIR/kvalidator.cmd" << 'EOF'
@echo off
set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%kvalidator.jar

if not exist "%JAR_FILE%" (
    echo JAR file not found: %JAR_FILE%
    exit /b 1
)

if not exist "validation-config.yaml" (
    echo validation-config.yaml not found, copying default...
    copy "%SCRIPT_DIR%validation-config.yaml" .
)

java -jar "%JAR_FILE%" %*
EOF

chmod +x "$RELEASE_DIR/run.sh"

# Create archive
echo "Creating archive..."
tar -czf kvalidator-$VERSION.tar.gz "$RELEASE_DIR"
zip -r -q kvalidator-$VERSION.zip "$RELEASE_DIR"

echo ""
echo "✅ Package created successfully!"
echo ""
echo "📁 Release directory: $RELEASE_DIR/"
echo "📦 Archives:"
echo "   - kvalidator-$VERSION.tar.gz ($(du -h kvalidator-$VERSION.tar.gz | cut -f1))"
echo "   - kvalidator-$VERSION.zip ($(du -h kvalidator-$VERSION.zip | cut -f1))"
echo ""
echo "🚀 To use:"
echo "   tar -xzf kvalidator-$VERSION.tar.gz"
echo "   cd $RELEASE_DIR"
echo "   ./run.sh --help"
