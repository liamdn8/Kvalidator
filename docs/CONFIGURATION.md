# Validation Configuration Guide

## Overview

The `validation-config.yaml` file controls which fields are ignored during infrastructure comparison. This helps focus on meaningful differences by filtering out fields that naturally vary between environments.

## Configuration File Location

- **Default location**: `src/main/resources/validation-config.yaml` (built into JAR)
- **Custom location**: Use `-f` or `--config` option to specify a custom config file

## Configuration Format

```yaml
ignoreFields:
  - "field.path.to.ignore"
  - "another.field.path"
  - "parent.field"  # This ignores parent.field.* as well
```

### Field Path Syntax

- **Full path**: Use dot notation starting from the flattened field path
  - Example: `metadata.creationTimestamp`, `spec.replicas`
  
- **Prefix matching**: Fields are matched by prefix
  - `"status"` matches `status`, `status.replicas`, `status.conditions[0].type`, etc.
  - `"metadata.annotations.kubectl"` matches all kubectl annotations

### Common Ignored Fields

#### Metadata Fields (Always Different)

```yaml
ignoreFields:
  - "metadata.creationTimestamp"   # Creation time varies
  - "metadata.generation"          # Internal K8s tracking
  - "metadata.resourceVersion"     # Internal K8s version
  - "metadata.uid"                 # Unique identifier
  - "metadata.selfLink"            # Deprecated field
  - "metadata.managedFields"       # Field management metadata
```

#### Status Fields (Runtime State)

```yaml
ignoreFields:
  - "status"  # All status fields (runtime state, not config)
```

#### Service-Specific Fields

```yaml
ignoreFields:
  - "spec.clusterIP"        # Auto-assigned cluster IP
  - "spec.clusterIPs"       # Auto-assigned IPs (multi-family)
  - "spec.ipFamilies"       # IP family configuration
  - "spec.ipFamilyPolicy"   # IP family policy
```

#### Deployment/Pod Template Fields

```yaml
ignoreFields:
  - "spec.template.metadata.creationTimestamp"
  - "spec.template.spec.nodeName"          # Runtime assignment
  - "spec.template.spec.restartPolicy"     # Often defaulted
  - "spec.template.spec.dnsPolicy"         # Often defaulted
  - "spec.template.spec.schedulerName"     # Often defaulted
  - "spec.template.spec.securityContext"   # Often defaulted
```

#### Kubectl Annotations (Large JSON Blocks)

```yaml
ignoreFields:
  - "metadata.annotations.kubectl.kubernetes.io/last-applied-configuration"
  - "metadata.annotations.deployment.kubernetes.io/revision"
```

## Usage Examples

### Using Default Configuration

```bash
# Uses built-in validation-config.yaml
java -jar kvalidator.jar app-dev app-prod
```

### Using Custom Configuration

```bash
# Use custom config file
java -jar kvalidator.jar -f /path/to/custom-config.yaml app-dev app-prod
```

### Sample Custom Configuration

Create `my-validation-config.yaml`:

```yaml
ignoreFields:
  # Ignore all metadata and status
  - "metadata"
  - "status"
  
  # Ignore specific deployment fields
  - "spec.replicas"                    # Ignore replica count differences
  - "spec.template.spec.containers[0].image"  # Ignore image version
  
  # Ignore all annotations
  - "metadata.annotations"
```

Then use it:

```bash
java -jar kvalidator.jar -f my-validation-config.yaml app-dev app-prod
```

## Field Path Examples

When comparing Kubernetes resources, fields are flattened into paths:

| Kubernetes YAML | Flattened Path |
|-----------------|----------------|
| `metadata.name` | `metadata.name` |
| `metadata.labels.app` | `metadata.labels.app` |
| `spec.replicas` | `spec.replicas` |
| `spec.template.spec.containers[0].image` | `spec.template.spec.containers[0].image` |
| `spec.ports[0].port` | `spec.ports[0].port` |

## Best Practices

### 1. Start with Default Config

The built-in config covers most common cases. Start there and add custom rules as needed.

### 2. Use Prefix Matching

Instead of ignoring individual fields:
```yaml
# ❌ Not recommended - verbose
ignoreFields:
  - "status.replicas"
  - "status.conditions"
  - "status.observedGeneration"
```

Use prefix matching:
```yaml
# ✅ Recommended - concise
ignoreFields:
  - "status"
```

### 3. Environment-Specific Configs

Create different configs for different comparison scenarios:

- `validation-config-strict.yaml`: Minimal ignores for strict comparison
- `validation-config-relaxed.yaml`: More ignores for loose comparison
- `validation-config-design.yaml`: Ignore runtime fields when comparing design vs reality

### 4. Document Custom Rules

Add comments to explain why fields are ignored:

```yaml
ignoreFields:
  # Runtime fields - not part of desired state
  - "status"
  - "metadata.resourceVersion"
  
  # Development vs Production differences - expected to vary
  - "spec.replicas"              # Different scale in each environment
  - "spec.resources.limits"      # Resource limits vary by environment
```

## Testing Your Configuration

Use verbose mode to see which differences remain after filtering:

```bash
# See detailed differences
java -jar kvalidator.jar -v -f my-config.yaml app-dev app-prod

# Compare to default config
java -jar kvalidator.jar -v app-dev app-prod
```

## Current Default Configuration

The default `validation-config.yaml` includes 21 ignore rules covering:

✅ Metadata timestamps and UIDs  
✅ Status and runtime fields  
✅ Service ClusterIPs  
✅ Kubectl annotations  
✅ Pod template defaults  

See `src/main/resources/validation-config.yaml` for the complete list.
