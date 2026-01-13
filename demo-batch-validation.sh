#!/bin/bash

# Demo script for KValidator Batch Validation
# This demonstrates batch validation feature with sample data

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║       KValidator - Batch Validation Demo                         ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

# Create demo reports directory
mkdir -p demo-reports

echo "📝 Demo Setup:"
echo "   - Baseline: baseline-design.yaml"
echo "   - Environments: demo-dev.yaml, demo-staging.yaml, demo-prod.yaml"
echo "   - Batch request: demo-validation-request.yaml"
echo ""

echo "📋 Batch Request Configuration:"
echo "   - 2 comparison requests"
echo "   - Sequential execution"
echo "   - Generate consolidated summary report"
echo ""

echo "💡 Expected Results:"
echo "   1. Individual Excel reports for each comparison"
echo "   2. Consolidated batch-summary-report.xlsx with:"
echo "      - Sheet 1: Batch Summary (overview of all comparisons)"
echo "      - Sheet 2-3: dev-staging-comparison (Summary + Details)"
echo "      - Sheet 4-5: all-envs-comparison (Summary + Details)"
echo ""

echo "📊 Excel Report Structure:"
echo "   Sheet 1 - Batch Summary:"
echo "      Column A: STT (Sequential number)"
echo "      Column B: Comparison Name"
echo "      Column C+: Each namespace/VIM with status:"
echo "                 - BASELINE: Baseline reference"
echo "                 - MATCH: Perfect match with baseline"
echo "                 - DIFFERENT: Has differences"
echo "                 - MISSING: Object missing"
echo "                 - MISMATCH: Partial mismatch"
echo ""
echo "   Individual Comparison Sheets:"
echo "      Summary: Object-level comparison matrix"
echo "      Details: Field-level differences"
echo ""

echo "🔍 Sample Differences in Demo Data:"
echo "   Dev environment:"
echo "      - replicas: 2 (baseline: 3)"
echo "      - loglevel: DEBUG (baseline: INFO)"
echo "      - environment label: development"
echo ""
echo "   Staging environment:"
echo "      - Matches baseline mostly"
echo "      - environment label: staging"
echo ""
echo "   Production environment:"
echo "      - replicas: 5 (baseline: 3)"
echo "      - image: nginx:1.22 (baseline: nginx:1.21)"
echo "      - loglevel: WARN (baseline: INFO)"
echo ""

echo "Note: This is a demonstration with YAML files."
echo "In production, you would compare live Kubernetes namespaces."
echo ""
echo "To run actual batch validation with Kubernetes clusters, use:"
echo "  java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \\"
echo "       -r validation-request.yaml"
echo ""

echo "═══════════════════════════════════════════════════════════════════"
echo "Demo completed! Check the explanation above for expected output."
echo "═══════════════════════════════════════════════════════════════════"
