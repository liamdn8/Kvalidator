#!/bin/bash
# Test with requested namespaces

echo "🧪 Testing with namespaces: app-dev, app-staging, app-production"

RESPONSE=$(curl -s -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "namespaces": ["app-dev", "app-staging", "app-production"],
    "description": "User requested validation"
  }')

echo "Response: $RESPONSE"

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" = "null" ]; then
    echo "❌ Failed to submit job"
    exit 1
fi

echo "✅ Job submitted: $JOB_ID"
echo "⏳ Polling status..."

while true; do
    JOB_STATUS=$(curl -s http://localhost:8080/api/validate/$JOB_ID)
    STATUS=$(echo "$JOB_STATUS" | jq -r '.status')
    PERCENT=$(echo "$JOB_STATUS" | jq -r '.progress.percentage // 0')
    STEP=$(echo "$JOB_STATUS" | jq -r '.progress.currentStep // ""')
    
    echo "Status: $STATUS ($PERCENT%) - $STEP"
    
    if [ "$STATUS" = "COMPLETED" ]; then
        echo "✅ Validation completed!"
        curl -O -J http://localhost:8080/api/validate/$JOB_ID/download
        echo "📥 Report downloaded"
        break
    fi
    
    if [ "$STATUS" = "FAILED" ]; then
        echo "❌ Validation failed: $(echo "$JOB_STATUS" | jq -r '.message')"
        break
    fi
    
    sleep 2
done
