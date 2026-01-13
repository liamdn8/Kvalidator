#!/bin/bash

# Demo script for KValidator REST API
# This script demonstrates the complete workflow of submitting and tracking a validation job

set -e

BASE_URL="http://localhost:8080/api/validate"

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║         KValidator REST API Demo                                 ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

# Check if server is running
echo "🔍 Checking if API server is running..."
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/openapi | grep -q "200"; then
    echo "❌ Error: API server is not running!"
    echo "   Please start the server with: mvn quarkus:dev"
    exit 1
fi
echo "✅ Server is running"
echo ""

# 1. Submit validation job
echo "📤 Step 1: Submitting validation job..."
echo ""

REQUEST_PAYLOAD='{
  "namespaces": ["default", "kube-system"],
  "description": "Compare default and kube-system namespaces",
  "exportExcel": true
}'

echo "Request payload:"
echo "$REQUEST_PAYLOAD" | jq '.'
echo ""

RESPONSE=$(curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "$REQUEST_PAYLOAD")

echo "Response:"
echo "$RESPONSE" | jq '.'
echo ""

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" = "null" ] || [ -z "$JOB_ID" ]; then
    echo "❌ Failed to submit job"
    exit 1
fi

echo "✅ Job submitted successfully!"
echo "   Job ID: $JOB_ID"
echo ""

# 2. Poll for job completion
echo "⏳ Step 2: Polling for job completion..."
echo ""

MAX_ATTEMPTS=60
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 2
    ATTEMPT=$((ATTEMPT + 1))
    
    JOB_STATUS=$(curl -s "$BASE_URL/$JOB_ID")
    STATUS=$(echo "$JOB_STATUS" | jq -r '.status')
    
    echo "[$ATTEMPT/$MAX_ATTEMPTS] Status: $STATUS"
    
    if [ "$STATUS" = "PROCESSING" ]; then
        PROGRESS=$(echo "$JOB_STATUS" | jq -r '.progress.percentage // 0')
        CURRENT_STEP=$(echo "$JOB_STATUS" | jq -r '.progress.currentStep // "Processing"')
        echo "             Progress: $PROGRESS% - $CURRENT_STEP"
    fi
    
    if [ "$STATUS" = "COMPLETED" ]; then
        echo ""
        echo "✅ Job completed successfully!"
        echo ""
        echo "Full status:"
        echo "$JOB_STATUS" | jq '{
            status,
            objectsCompared,
            differencesFound,
            reportPath,
            downloadUrl,
            jsonUrl,
            submittedAt,
            completedAt
        }'
        echo ""
        break
    fi
    
    if [ "$STATUS" = "FAILED" ]; then
        echo ""
        echo "❌ Job failed!"
        echo "Error message:"
        echo "$JOB_STATUS" | jq -r '.message'
        exit 1
    fi
done

if [ "$STATUS" != "COMPLETED" ]; then
    echo "⏰ Timeout waiting for job completion"
    exit 1
fi

# 3. Download Excel report
echo "📥 Step 3: Downloading Excel report..."
EXCEL_FILE="validation-report-$JOB_ID.xlsx"

curl -s -o "$EXCEL_FILE" "$BASE_URL/$JOB_ID/download"

if [ -f "$EXCEL_FILE" ]; then
    FILE_SIZE=$(ls -lh "$EXCEL_FILE" | awk '{print $5}')
    echo "✅ Excel report downloaded: $EXCEL_FILE ($FILE_SIZE)"
    echo ""
else
    echo "❌ Failed to download Excel report"
fi

# 4. Get JSON results
echo "📊 Step 4: Getting JSON results..."
echo ""

JSON_RESULTS=$(curl -s "$BASE_URL/$JOB_ID/json")

echo "Summary:"
echo "$JSON_RESULTS" | jq '.summary'
echo ""

echo "Comparison keys:"
echo "$JSON_RESULTS" | jq -r '.comparisons | keys[]'
echo ""

# Save JSON to file
JSON_FILE="validation-results-$JOB_ID.json"
echo "$JSON_RESULTS" | jq '.' > "$JSON_FILE"
echo "✅ JSON results saved to: $JSON_FILE"
echo ""

# 5. Display summary
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                    Demo Completed                                 ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Files created:"
echo "  - $EXCEL_FILE (Excel report)"
echo "  - $JSON_FILE (JSON results)"
echo ""
echo "API Endpoints used:"
echo "  1. POST   $BASE_URL"
echo "  2. GET    $BASE_URL/$JOB_ID"
echo "  3. GET    $BASE_URL/$JOB_ID/download"
echo "  4. GET    $BASE_URL/$JOB_ID/json"
echo ""
echo "Next steps:"
echo "  - View Swagger UI: http://localhost:8080/swagger-ui"
echo "  - Check results directory: /tmp/.kvalidator/results/$JOB_ID"
echo ""
