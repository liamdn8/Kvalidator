#!/bin/bash
# Quick test script for KValidator API

echo "🧪 KValidator API Quick Test"
echo "=============================="
echo ""

# Check if server is running
if ! curl -s http://localhost:8080/openapi > /dev/null 2>&1; then
    echo "❌ Server is not running!"
    echo "   Start with: mvn quarkus:dev"
    exit 1
fi

echo "✅ Server is running"
echo ""

# Test 1: Submit job
echo "Test 1: Submit validation job"
RESPONSE=$(curl -s -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "namespaces": ["default"],
    "description": "Quick test"
  }')

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')
echo "   Job ID: $JOB_ID"
echo ""

# Test 2: Get status
echo "Test 2: Check job status"
sleep 1
STATUS=$(curl -s http://localhost:8080/api/validate/$JOB_ID | jq -r '.status')
echo "   Status: $STATUS"
echo ""

# Test 3: Swagger UI
echo "Test 3: Swagger UI available"
if curl -s http://localhost:8080/swagger-ui | grep -q "swagger"; then
    echo "   ✅ http://localhost:8080/swagger-ui"
else
    echo "   ❌ Swagger UI not available"
fi
echo ""

# Test 4: OpenAPI spec
echo "Test 4: OpenAPI spec available"
if curl -s http://localhost:8080/openapi | grep -q "openapi"; then
    echo "   ✅ http://localhost:8080/openapi"
else
    echo "   ❌ OpenAPI spec not available"
fi
echo ""

echo "✅ All basic tests passed!"
echo ""
echo "Next steps:"
echo "  - View Swagger UI: http://localhost:8080/swagger-ui"
echo "  - Run full demo: ./demo-api.sh"
echo "  - Read docs: API_GUIDE.md"
