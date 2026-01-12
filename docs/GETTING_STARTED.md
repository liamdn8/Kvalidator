# Getting Started with KValidator

## Quick Setup Guide

### 1. Verify Prerequisites

```bash
# Check Java version (should be 11 or higher)
java -version

# Check Maven version (should be 3.6 or higher)
mvn -version
```

### 2. Build the Project

```bash
# Navigate to project directory
cd Kvalidator

# Clean and build
mvn clean package

# Verify build success
ls -lh target/kvalidator-*.jar
```

### 3. Prepare Your First Validation

#### Option A: Design Validation

Create a design specification file `my-design.yaml`:

```yaml
version: "1.0"
designName: "My NFV Environment"

cluster:
  name: "my-cluster"
  version: "1.28"

workloads:
  - name: "my-app"
    namespace: "default"
    replicas: 2
    resources:
      requests:
        cpu: "500m"
        memory: "1Gi"
      limits:
        cpu: "1000m"
        memory: "2Gi"
```

Run validation:

```bash
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -d my-design.yaml \
  -k ~/.kube/config \
  -v
```

#### Option B: Environment Comparison

If you have multiple kubeconfig files:

```bash
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -c env1,env2 \
  -k config1.yaml,config2.yaml \
  -o json
```

### 4. Review the Results

The validation results will be displayed in the console. For HTML output:

```bash
java -jar target/kvalidator-*.jar \
  -d my-design.yaml \
  -k ~/.kube/config \
  -o html > report.html

# Open in browser
xdg-open report.html  # Linux
# or
open report.html      # macOS
```

## Next Steps

1. **Customize Validation Rules**: Edit `src/main/resources/validation-config.yaml`
2. **Read Full Documentation**: See `docs/USER_GUIDE.md`
3. **Explore Examples**: Check `src/main/resources/examples/`
4. **Set Up Automation**: Create shell scripts for regular validations

## Common Use Cases

### Daily Production Validation

Create a script `validate-prod.sh`:

```bash
#!/bin/bash
TODAY=$(date +%Y%m%d)
REPORT_DIR="reports"
mkdir -p "$REPORT_DIR"

java -jar target/kvalidator-*.jar \
  -d designs/production.yaml \
  -k ~/.kube/config-prod \
  -o json > "$REPORT_DIR/validation-$TODAY.json"

echo "Validation completed: $REPORT_DIR/validation-$TODAY.json"
```

### Weekly Environment Comparison

Create a script `compare-weekly.sh`:

```bash
#!/bin/bash
WEEK=$(date +%Y-W%V)
REPORT_DIR="reports"
mkdir -p "$REPORT_DIR"

java -jar target/kvalidator-*.jar \
  -c production,staging \
  -k configs/prod.yaml,configs/staging.yaml \
  -o html > "$REPORT_DIR/comparison-$WEEK.html"

echo "Comparison completed: $REPORT_DIR/comparison-$WEEK.html"
```

## Troubleshooting

### Issue: Cannot connect to cluster

**Solution**: Verify your kubeconfig file:
```bash
kubectl --kubeconfig=<your-config> cluster-info
```

### Issue: Out of memory

**Solution**: Increase JVM heap size:
```bash
java -Xmx2g -jar target/kvalidator-*.jar ...
```

### Issue: Permission denied

**Solution**: Check RBAC permissions:
```bash
kubectl --kubeconfig=<your-config> auth can-i get pods --all-namespaces
```

## Getting Help

- See detailed documentation in `docs/`
- Check example files in `src/main/resources/examples/`
- Review logs in `logs/kvalidator.log`
- Use verbose mode: `-v` or `--verbose`
