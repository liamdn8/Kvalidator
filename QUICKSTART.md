# Quick Start Guide

## Installation

1. **Verify Prerequisites**
   ```bash
   java -version  # Should be Java 11+
   kubectl version  # Should connect to cluster
   ```

2. **Copy JAR and Config**
   ```bash
   # JAR is already built at: target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar
   
   # Copy config file to working directory
   cp src/main/resources/validation-config.yaml .
   ```

## Basic Usage

### Method 1: Use the run script (Linux/Mac)
```bash
./run.sh app-dev app-staging
./run.sh -b baseline.yaml app-prod
./run.sh -o report.xlsx app-dev app-staging app-prod
```

### Method 2: Use Java directly
```bash
# Simple comparison
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar app-dev app-staging

# With baseline
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -b baseline-design.yaml \
  -o report.xlsx \
  app-dev app-staging app-prod

# Verbose mode
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar -v app-dev app-prod
```

## First Test

Test with the included baseline example:

```bash
# 1. Make sure validation-config.yaml is in current directory
cp src/main/resources/validation-config.yaml .

# 2. Run comparison (requires app-dev namespace in your cluster)
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  -b baseline-design.yaml \
  -v \
  app-dev
```

## Common Commands

```bash
# Compare two namespaces
./run.sh app-dev app-prod

# Compare with baseline and export
./run.sh -b baseline.yaml -o report.xlsx app-dev app-staging

# Compare only Deployments and Services
./run.sh -k Deployment,Service app-dev app-prod

# Verbose output
./run.sh -v app-dev app-staging

# Full example with all options
./run.sh -b baseline/ -k Deployment,Service,ConfigMap -v -o full-report.xlsx app-dev app-staging app-prod
```

## Next Steps

1. Read [README.md](README.md) for full documentation
2. See [USAGE.md](USAGE.md) for detailed examples and use cases
3. Customize `validation-config.yaml` for your needs
4. Create your own baseline YAML files for your infrastructure

## Need Help?

```bash
./run.sh --help
```

Or:
```bash
java -jar target/kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help
```
