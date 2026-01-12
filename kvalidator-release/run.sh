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
