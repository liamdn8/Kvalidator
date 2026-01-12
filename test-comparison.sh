#!/bin/bash
# Quick test script for KValidator

JAR="target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

echo "================================"
echo "KValidator Quick Test"
echo "================================"
echo ""

echo "Test 1: Compare app-dev vs app-staging (with config filtering)"
echo "--------------------------------------------------------------"
java -jar "$JAR" -v app-dev app-staging 2>&1 | grep -A 80 "Comparison Results"
echo ""

echo ""
echo "Test 2: Compare only Deployments and Services"
echo "--------------------------------------------------------------"
java -jar "$JAR" -k Deployment,Service app-dev app-prod 2>&1 | grep -A 40 "Comparison Results"
echo ""

echo ""
echo "Test 3: Compare 3 namespaces"
echo "--------------------------------------------------------------"
java -jar "$JAR" app-dev app-staging app-prod 2>&1 | grep -A 60 "Comparison Results"
echo ""
