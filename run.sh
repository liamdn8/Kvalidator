#!/bin/bash

# KValidator - Quick run script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Please build first: mvn clean package"
    exit 1
fi

# Check if validation-config.yaml exists in current directory
if [ ! -f "./validation-config.yaml" ]; then
    echo "⚠️  validation-config.yaml not found in current directory"
    echo "Copying default config..."
    if [ -f "$SCRIPT_DIR/src/main/resources/validation-config.yaml" ]; then
        cp "$SCRIPT_DIR/src/main/resources/validation-config.yaml" .
        echo "✅ Copied validation-config.yaml"
    fi
fi

# Run KValidator
java -jar "$JAR_FILE" "$@"
