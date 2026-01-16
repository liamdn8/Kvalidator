#!/bin/bash

# Verification script for KValidator API
# Tests the full flow: Submit -> Poll -> Download with real cluster data

set -e

# Configuration
API_URL="http://localhost:8080/kvalidator/api/validate"
REQUEST_FILE="examples/full-cycle-test.json"
OUTPUT_DIR="test-output"

mkdir -p "$OUTPUT_DIR"

echo "🧪 Starting API Verification Test"
echo "================================"
echo "Target namespaces: app-dev, app-staging, app-prod"
echo ""

# 1. Check Server Health
echo "1️⃣  Checking Server Health..."
if curl -s http://localhost:8080/q/health | grep -q "UP"; then
    echo "   ✅ Server is UP"
else
    # Fallback check for OpenAPI if health check is not enabled
    if curl -s http://localhost:8080/kvalidator/api/openapi > /dev/null; then
        echo "   ✅ Server is UP (OpenAPI accessible)"
    else
        echo "   ❌ Server is NOT reachable at localhost:8080"
        exit 1
    fi
fi
echo ""

# 2. Submit Validation Job
echo "2️⃣  Submitting Validation Job..."
echo "   Payload source: $REQUEST_FILE"
cat $REQUEST_FILE
echo ""

RESPONSE=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d @"$REQUEST_FILE")

echo "   Response: $RESPONSE"

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
    echo "   ❌ Failed to submit job. No Job ID received."
    exit 1
fi

echo "   ✅ Job Submitted. ID: $JOB_ID"
echo ""

# 3. Poll for Completion
echo "3️⃣  Polling for Completion..."
STATUS="PENDING"
START_TIME=$(date +%s)

while true; do
    JOB_INFO=$(curl -s "$API_URL/$JOB_ID")
    STATUS=$(echo "$JOB_INFO" | jq -r '.status')
    
    # Calculate elapsed time
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [ "$STATUS" == "PROCESSING" ]; then
        PROGRESS=$(echo "$JOB_INFO" | jq -r '.progress.percentage // 0')
        STEP=$(echo "$JOB_INFO" | jq -r '.progress.currentStep // "Running"')
        echo -ne "   ⏳ [${ELAPSED}s] Status: $STATUS ($PROGRESS%) - $STEP\r"
    elif [ "$STATUS" == "COMPLETED" ]; then
        echo -e "\n   ✅ Job Completed in ${ELAPSED}s"
        break
    elif [ "$STATUS" == "FAILED" ]; then
        MESSAGE=$(echo "$JOB_INFO" | jq -r '.message')
        echo -e "\n   ❌ Job Failed: $MESSAGE"
        exit 1
    else
        echo -ne "   ⏳ [${ELAPSED}s] Status: $STATUS\r"
    fi
    
    if [ $ELAPSED -gt 120 ]; then
        echo -e "\n   ⏰ Timeout waiting for job completion"
        exit 1
    fi
    
    sleep 2
done
echo ""

# 4. Analyze Results
echo "4️⃣  Analyzing Results..."
# Get JSON Details
JSON_RESULT=$(curl -s "$API_URL/$JOB_ID/json")
TOTAL_DIFF=$(echo "$JSON_RESULT" | jq '.summary.totalDifferences')
TOTAL_OBJ=$(echo "$JSON_RESULT" | jq '.summary.totalObjects')
PAIRS=$(echo "$JSON_RESULT" | jq '.summary.namespacePairs')

echo "   📊 Summary Stats:"
echo "      - Objects Compared: $TOTAL_OBJ"
echo "      - Total Differences: $TOTAL_DIFF"
echo "      - Namespace Pairs: $PAIRS"

# Save JSON
JSON_FILE="$OUTPUT_DIR/result-$JOB_ID.json"
echo "$JSON_RESULT" > "$JSON_FILE"
echo "   ✅ Saved JSON result to $JSON_FILE"

# 5. Download Report
echo "5️⃣  Downloading Excel Report..."
EXCEL_FILE="$OUTPUT_DIR/report-$JOB_ID.xlsx"
HTTP_CODE=$(curl -s -o "$EXCEL_FILE" -w "%{http_code}" "$API_URL/$JOB_ID/download")

if [ "$HTTP_CODE" == "200" ]; then
    if [ -s "$EXCEL_FILE" ]; then
        SIZE=$(du -h "$EXCEL_FILE" | cut -f1)
        echo "   ✅ Downloaded Excel report: $EXCEL_FILE ($SIZE)"
    else
        echo "   ❌ File downloaded but empty"
        exit 1
    fi
else
    echo "   ❌ Failed to download report. HTTP Code: $HTTP_CODE"
    exit 1
fi
echo ""

echo "✨ Test Cycle Completed Successfully! ✨"
