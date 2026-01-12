# Comparison Framework - Class Structure

## Tổng quan cấu trúc so sánh phân tầng

KValidator sử dụng cấu trúc comparison với tên nhất quán theo cấp độ:

```
KeyComparison          → So sánh field đơn lẻ
ObjectComparison       → So sánh Kubernetes object (chứa nhiều KeyComparison)
NamespaceComparison    → So sánh namespace (chứa nhiều ObjectComparison)
```

## 1. KeyComparison (Field Level)

**File**: [KeyComparison.java](../src/main/java/com/nfv/validator/model/KeyComparison.java)

**Mục đích**: Đại diện cho comparison của một field đơn lẻ

**Properties**:
- `key`: String - Tên field hoặc đường dẫn (e.g., "spec.replicas", "metadata.name")
- `expectedValue`: String - Giá trị mong đợi
- `actualValue`: String - Giá trị thực tế
- `status`: ComparisonStatus - Kết quả so sánh (MATCH, MISMATCH, MISSING_IN_EXPECTED, MISSING_IN_ACTUAL)

**Methods**:
- `getStatus()`: ComparisonStatus - Tính toán và trả về status
- `isMatch()`: boolean - Kiểm tra có match không
- `hasDifference()`: boolean - Kiểm tra có khác biệt không

**Example**:
```java
KeyComparison comparison = new KeyComparison("cpu", "1000m", "2000m");
System.out.println(comparison.getStatus()); // MISMATCH
```

---

## 2. ObjectComparison (Object Level)

**File**: [ObjectComparison.java](../src/main/java/com/nfv/validator/model/ObjectComparison.java)

**Mục đích**: Đại diện cho comparison của một Kubernetes object (Deployment, Service, ConfigMap, etc.)

**Properties**:
- `resourceType`: String - Loại resource (e.g., "Deployment", "Service")
- `resourceName`: String - Tên resource
- `namespace`: String - Namespace
- `overallStatus`: ComparisonStatus - Status tổng thể
- `items`: List<KeyComparison> - Danh sách các field comparisons
- `message`: String - Thông báo bổ sung

**Methods**:
- `addItem(KeyComparison)`: void - Thêm field comparison
- `getDifferences()`: List<KeyComparison> - Lấy các fields có khác biệt
- `isFullMatch()`: boolean - Kiểm tra tất cả fields có match không
- `getDifferenceCount()`: int - Đếm số lượng differences

**Example**:
```java
Map<String, String> expected = K8sFlattener.flattenResource(expectedDeployment);
Map<String, String> actual = K8sFlattener.flattenResource(actualDeployment);

ObjectComparison result = Comparator.compareObject(
    "Deployment", "my-app", "default", expected, actual);

System.out.println("Status: " + result.getOverallStatus());
System.out.println("Differences: " + result.getDifferenceCount());
```

---

## 3. NamespaceComparison (Namespace Level)

**File**: [NamespaceComparison.java](../src/main/java/com/nfv/validator/model/NamespaceComparison.java)

**Mục đích**: Đại diện cho comparison của toàn bộ namespace

**Properties**:
- `namespaceName`: String - Tên namespace
- `clusterName`: String - Tên cluster (cho multi-cluster)
- `objectResults`: Map<String, List<ObjectComparison>> - Kết quả theo từng resource type
- `summary`: ComparisonSummary - Thống kê tổng hợp

**Methods**:
- `addObjectResult(ObjectComparison)`: void - Thêm object comparison
- `getAllObjectResults()`: List<ObjectComparison> - Lấy tất cả object results
- `getObjectResultsByType(String)`: List<ObjectComparison> - Lấy results theo type
- `getObjectsWithDifferences()`: List<ObjectComparison> - Lấy objects có khác biệt

**Nested Class - ComparisonSummary**:
- `totalObjects`: int
- `matchedObjects`: int
- `mismatchedObjects`: int
- `missingInExpected`: int
- `missingInActual`: int
- `getMatchPercentage()`: double

**Example**:
```java
NamespaceComparison result = NamespaceComparator.compareNamespace(
    "production", "cluster-1", expectedResources, actualResources);

System.out.println("Total objects: " + result.getSummary().getTotalObjects());
System.out.println("Match %: " + result.getSummary().getMatchPercentage());
```

---

## Comparison Status

**File**: [ComparisonStatus.java](../src/main/java/com/nfv/validator/model/ComparisonStatus.java)

**Enum values**:
- `MATCH` - Giá trị khớp hoàn toàn
- `MISMATCH` - Giá trị khác nhau
- `MISSING_IN_EXPECTED` - Có trong actual nhưng không có trong expected (extra field)
- `MISSING_IN_ACTUAL` - Có trong expected nhưng không có trong actual (missing field)

---

## Comparator Utilities

**File**: [Comparator.java](../src/main/java/com/nfv/validator/comparison/Comparator.java)

**Core comparison methods**:

### Field Level
```java
// So sánh một field
KeyComparison compareField(String key, String expected, String actual)

// So sánh nhiều fields
List<KeyComparison> compareFields(Map<String,String> expected, Map<String,String> actual)
```

### Object Level
```java
// So sánh một object
ObjectComparison compareObject(String type, String name, String ns, 
                               Map<String,String> expected, Map<String,String> actual)

// So sánh với null handling
ObjectComparison compareObjectSafe(String type, String name, String ns,
                                   Map<String,String> expected, Map<String,String> actual)

// So sánh nhiều objects cùng type
List<ObjectComparison> compareObjects(String type, String ns,
                                      Map<String,Map<String,String>> expected,
                                      Map<String,Map<String,String>> actual)
```

### Utility Methods
```java
// Filter theo status
List<KeyComparison> filterByStatus(List<KeyComparison> items, ComparisonStatus status)

// Get specific statuses
List<KeyComparison> getMismatches(List<KeyComparison> items)
List<KeyComparison> getMissingInActual(List<KeyComparison> items)
List<KeyComparison> getMissingInExpected(List<KeyComparison> items)

// Calculate statistics
double calculateMatchPercentage(List<KeyComparison> items)
```

---

## NamespaceComparator Utilities

**File**: [NamespaceComparator.java](../src/main/java/com/nfv/validator/comparison/NamespaceComparator.java)

**Methods**:

### Namespace Level
```java
// So sánh toàn bộ namespace
NamespaceComparison compareNamespace(String ns, String cluster,
                                     Map<String,Map<String,Map<String,String>>> expected,
                                     Map<String,Map<String,Map<String,String>>> actual)

// So sánh resource type cụ thể
List<ObjectComparison> compareResourceType(String ns, String cluster, String type,
                                           Map<String,Map<String,String>> expected,
                                           Map<String,Map<String,String>> actual)
```

### Multi-Cluster
```java
// So sánh namespace across clusters
Map<String,NamespaceComparison> compareNamespaceAcrossClusters(
    String ns,
    Map<String,Map<String,Map<String,Map<String,String>>>> clusterData)

// Aggregate summaries
NamespaceComparison.ComparisonSummary aggregateSummary(
    List<NamespaceComparison> results)
```

---

## K8s Flattener Utility

**File**: [K8sFlattener.java](../src/main/java/com/nfv/validator/util/K8sFlattener.java)

**Purpose**: Flatten Kubernetes objects thành key-value pairs để dễ so sánh

**Methods**:

```java
// Flatten basic
Map<String,String> flattenResource(Object resource)

// Flatten với default exclusions (uid, resourceVersion, status, etc.)
Map<String,String> flattenResourceWithDefaults(Object resource)

// Flatten với filter
Map<String,String> flattenResourceFiltered(Object resource, List<String> includePatterns)
Map<String,String> flattenResourceExcluded(Object resource, List<String> excludePatterns)

// Extract utilities
Map<String,String> extractFields(Map<String,String> flattened, List<String> fields)
Map<String,String> extractByPrefix(Map<String,String> flattened, String prefix)
Map<String,Map<String,String>> groupByCategory(Map<String,String> flattened)

// Flatten nhiều resources
Map<String,Map<String,String>> flattenResources(List<T> resources, 
                                                Function<T,String> nameExtractor)
```

**Example**:
```java
// Flatten Deployment
Map<String, String> flattened = K8sFlattener.flattenResourceWithDefaults(deployment);

// Result:
// {
//   "metadata.name": "my-app",
//   "metadata.namespace": "default",
//   "spec.replicas": "3",
//   "spec.template.spec.containers[0].image": "nginx:1.19"
// }
```

---

## Workflow Example

### Complete Comparison Workflow

```java
// 1. Flatten resources
Map<String, String> expectedFlat = K8sFlattener.flattenResourceWithDefaults(expectedDeployment);
Map<String, String> actualFlat = K8sFlattener.flattenResourceWithDefaults(actualDeployment);

// 2. Field-level comparison
List<KeyComparison> fieldComparisons = Comparator.compareFields(expectedFlat, actualFlat);

// 3. Object-level comparison
ObjectComparison objectResult = Comparator.compareObject(
    "Deployment", "my-app", "default", expectedFlat, actualFlat);

// 4. Get differences
List<KeyComparison> diffs = objectResult.getDifferences();
for (KeyComparison diff : diffs) {
    System.out.println(diff.getKey() + ": " + 
                      diff.getExpectedValue() + " → " + diff.getActualValue());
}

// 5. Namespace-level comparison
NamespaceComparison nsResult = NamespaceComparator.compareNamespace(
    "production", "cluster-1", expectedResources, actualResources);

System.out.println("Match rate: " + nsResult.getSummary().getMatchPercentage() + "%");
```

---

## Data Structure Hierarchy

```
Map<String, Map<String, Map<String, Map<String, String>>>>
     │         │          │              │
     │         │          │              └─> Field value
     │         │          └───────────────> Field path (e.g., "spec.replicas")
     │         └──────────────────────────> Resource name (e.g., "my-deployment")
     └────────────────────────────────────> Resource type (e.g., "Deployment")

For namespace comparison:
    clusterName -> resourceType -> resourceName -> fieldPath -> value

For multi-cluster:
    clusterName -> resourceType -> resourceName -> fieldPath -> value
```

---

## Testing

**Test files**:
- [ComparatorTest.java](../src/test/java/com/nfv/validator/comparison/ComparatorTest.java)
- [K8sFlattenerTest.java](../src/test/java/com/nfv/validator/util/K8sFlattenerTest.java)
- [ComparisonExample.java](../src/test/java/com/nfv/validator/comparison/ComparisonExample.java)

Run tests:
```bash
mvn test -Dtest=ComparatorTest
mvn test -Dtest=K8sFlattenerTest
```

Run example:
```bash
mvn exec:java -Dexec.mainClass="com.nfv.validator.comparison.ComparisonExample"
```
