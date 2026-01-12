# KValidator - Architecture Documentation

## Tổng quan kiến trúc (Architecture Overview)

KValidator được thiết kế theo kiến trúc modular với các component độc lập, dễ mở rộng và bảo trì.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    CLI Interface                             │
│              (CommandLineInterface)                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  Core Application                            │
│             (KValidatorApplication)                          │
└─────┬──────────────────────────────────────────┬────────────┘
      │                                           │
      ▼                                           ▼
┌──────────────────────┐              ┌────────────────────────┐
│  Design Validator    │              │ Environment Comparator │
│  (Feature 1)         │              │    (Feature 2)         │
└──────────┬───────────┘              └────────────┬───────────┘
           │                                       │
           └───────────────┬───────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Cluster Manager       │
              │  (Multi-cluster)       │
              └────────┬───────────────┘
                       │
                       ▼
              ┌────────────────────────┐
              │  Kubernetes Clusters   │
              │  (Prod/Staging/Dev)    │
              └────────────────────────┘
```

## Component Details

### 1. CLI Layer (`com.nfv.validator.cli`)

**Purpose**: Command-line interface handling

**Key Classes**:
- `CommandLineInterface`: Parses arguments, validates options, orchestrates execution

**Responsibilities**:
- Parse command-line arguments
- Validate input parameters
- Display help and usage information
- Route to appropriate feature handlers

### 2. Core Application (`com.nfv.validator`)

**Purpose**: Application entry point và orchestration

**Key Classes**:
- `KValidatorApplication`: Main entry point

**Responsibilities**:
- Initialize application
- Setup logging
- Handle global exception handling
- Coordinate component lifecycle

### 3. Design Validation (`com.nfv.validator.design`)

**Purpose**: Feature 1 - So sánh thiết kế vs thực tế

**Key Classes**:
- `DesignValidator`: Main validation logic
- `DesignSpecLoader`: Load và parse design specification
- `ClusterStateCollector`: Collect actual cluster state
- `ComparisonEngine`: Compare design vs reality

**Workflow**:
```
1. Load design specification from YAML file
2. Connect to target Kubernetes cluster
3. Collect actual state (deployments, services, etc.)
4. Compare design vs actual
5. Generate validation report with issues
```

### 4. Environment Comparison (`com.nfv.validator.comparison`)

**Purpose**: Feature 2 - So sánh các môi trường

**Key Classes**:
- `EnvironmentComparator`: Main comparison logic
- `EnvironmentStateCollector`: Collect state from each environment
- `DifferenceAnalyzer`: Analyze differences between environments
- `SimilarityDetector`: Identify common configurations

**Workflow**:
```
1. Connect to all target clusters
2. Collect configurations from each cluster
3. Normalize data structures
4. Perform comparison analysis
5. Generate comparison report
```

### 5. Kubernetes Integration (`com.nfv.validator.kubernetes`)

**Purpose**: Multi-cluster Kubernetes connectivity

**Key Classes**:
- `KubernetesClusterManager`: Manage multiple cluster connections
- `ClusterConnectionPool`: Connection pooling
- `ResourceCollector`: Generic Kubernetes resource collection

**Features**:
- Multiple cluster connection management
- Connection pooling và reuse
- Timeout handling
- Authentication support (kubeconfig, token, certificates)
- Read-only operations

### 6. Configuration (`com.nfv.validator.config`)

**Purpose**: Application configuration management

**Key Classes**:
- `ValidationConfig`: Validation rules configuration
- `ApplicationConfig`: Global settings
- `ConfigLoader`: Load configuration from files

### 7. Data Models (`com.nfv.validator.model`)

**Purpose**: Domain objects và DTOs

**Key Classes**:
- `ValidationResult`: Design validation results
- `ComparisonResult`: Environment comparison results
- `ClusterState`: Kubernetes cluster state snapshot
- `DesignSpecification`: Design specification model

## Design Patterns

### 1. Strategy Pattern
- Different validation strategies for different resource types
- Pluggable comparison algorithms

### 2. Factory Pattern
- `KubernetesClientFactory`: Create clients with different configurations
- `ReportGeneratorFactory`: Create different report formats (JSON/YAML/HTML)

### 3. Builder Pattern
- `ValidationResult.Builder`: Build validation results
- `ComparisonResult.Builder`: Build comparison results

### 4. Repository Pattern
- Abstract Kubernetes resource access
- Easy to mock for testing

## Data Flow

### Design Validation Flow

```
User Input (CLI)
    ↓
CommandLineInterface
    ↓
DesignValidator.validate()
    ↓
DesignSpecLoader.load() → Design Model
    ↓
KubernetesClusterManager.getClient()
    ↓
ClusterStateCollector.collect() → Actual State
    ↓
ComparisonEngine.compare(design, actual)
    ↓
ValidationResult (with issues)
    ↓
ReportGenerator.generate()
    ↓
Output (JSON/YAML/HTML)
```

### Environment Comparison Flow

```
User Input (CLI)
    ↓
CommandLineInterface
    ↓
EnvironmentComparator.compare()
    ↓
For each cluster:
    KubernetesClusterManager.getClient()
        ↓
    EnvironmentStateCollector.collect()
        ↓
    ClusterState snapshot
    ↓
DifferenceAnalyzer.analyze(states)
    ↓
ComparisonResult
    ↓
ReportGenerator.generate()
    ↓
Output (JSON/YAML/HTML)
```

## Package Structure

```
com.nfv.validator/
├── KValidatorApplication.java          # Main entry point
├── cli/                                # Command-line interface
│   └── CommandLineInterface.java
├── design/                             # Feature 1: Design Validation
│   ├── DesignValidator.java
│   ├── DesignSpecLoader.java
│   ├── ClusterStateCollector.java
│   └── ComparisonEngine.java
├── comparison/                         # Feature 2: Environment Comparison
│   ├── EnvironmentComparator.java
│   ├── EnvironmentStateCollector.java
│   ├── DifferenceAnalyzer.java
│   └── SimilarityDetector.java
├── kubernetes/                         # Kubernetes integration
│   ├── KubernetesClusterManager.java
│   ├── ResourceCollector.java
│   └── ClientFactory.java
├── config/                            # Configuration
│   ├── ValidationConfig.java
│   ├── ApplicationConfig.java
│   └── ConfigLoader.java
├── model/                             # Domain models
│   ├── ValidationResult.java
│   ├── ComparisonResult.java
│   ├── ClusterState.java
│   └── DesignSpecification.java
├── report/                            # Report generation
│   ├── ReportGenerator.java
│   ├── JsonReportGenerator.java
│   ├── YamlReportGenerator.java
│   └── HtmlReportGenerator.java
└── util/                              # Utilities
    ├── YamlUtils.java
    ├── JsonUtils.java
    └── ValidationUtils.java
```

## Technology Stack

### Core
- **Java 11**: Programming language
- **Maven**: Dependency management và build

### Libraries
- **Fabric8 Kubernetes Client**: Kubernetes API interaction
- **Jackson**: JSON/YAML processing
- **SnakeYAML**: YAML parsing
- **SLF4J + Logback**: Logging framework
- **Apache Commons CLI**: Command-line parsing
- **Apache Commons Lang3**: Utility functions
- **Lombok**: Reduce boilerplate code

### Testing
- **JUnit 5**: Unit testing framework
- **Mockito**: Mocking framework
- **AssertJ**: Fluent assertions

## Extension Points

### 1. Custom Validation Rules
Implement `ValidationRule` interface:

```java
public interface ValidationRule {
    ValidationIssue validate(ClusterState actual, DesignSpecification design);
}
```

### 2. Custom Report Generators
Implement `ReportGenerator` interface:

```java
public interface ReportGenerator {
    String generate(ValidationResult result);
}
```

### 3. Custom Resource Collectors
Extend `ResourceCollector`:

```java
public abstract class ResourceCollector<T> {
    abstract List<T> collect(KubernetesClient client);
}
```

## Performance Considerations

### 1. Parallel Processing
- Collect from multiple clusters in parallel
- Use CompletableFuture for async operations

### 2. Caching
- Cache cluster states for repeated queries
- TTL-based cache invalidation

### 3. Resource Optimization
- Stream processing for large datasets
- Limit resource collection scope when possible

### 4. Connection Management
- Connection pooling
- Reuse connections across operations
- Proper resource cleanup

## Security Considerations

### 1. Credential Management
- Never log credentials
- Support for external secret management
- Secure kubeconfig handling

### 2. Read-Only Operations
- Only read operations on clusters
- No cluster state modifications
- RBAC validation

### 3. TLS/SSL
- Verify certificates by default
- Option to skip verification (development only)

## Error Handling

### 1. Validation Errors
- Continue on error vs fail fast (configurable)
- Collect all errors before reporting

### 2. Connection Errors
- Retry with exponential backoff
- Timeout handling
- Graceful degradation

### 3. Configuration Errors
- Validate configuration on startup
- Clear error messages
- Fail fast for critical errors

## Testing Strategy

### 1. Unit Tests
- Test individual components
- Mock Kubernetes clients
- Test validation logic

### 2. Integration Tests
- Test with mock Kubernetes clusters
- Test multi-cluster scenarios
- Test different authentication methods

### 3. End-to-End Tests
- Test complete workflows
- Test with real design specifications
- Test report generation

## Future Enhancements

### Phase 2
- [ ] Web UI dashboard
- [ ] REST API server mode
- [ ] Webhook support for continuous monitoring
- [ ] Integration with GitOps tools

### Phase 3
- [ ] Machine learning for anomaly detection
- [ ] Predictive analysis
- [ ] Auto-remediation capabilities
- [ ] Multi-cloud support (AWS EKS, Azure AKS, GCP GKE)
