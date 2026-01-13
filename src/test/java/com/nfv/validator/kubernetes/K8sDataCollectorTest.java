package com.nfv.validator.kubernetes;

import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for K8sDataCollector
 * Requires a running Kubernetes cluster
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class K8sDataCollectorTest {

    private static KubernetesClient client;
    private static K8sDataCollector collector;
    private static boolean clusterAvailable = false;

    @BeforeAll
    static void setUp() {
        try {
            // Try to connect to default cluster
            Config config = Config.autoConfigure(null);
            client = new DefaultKubernetesClient(config);
            
            // Test connection by listing namespaces
            client.namespaces().list();
            
            collector = new K8sDataCollector(client);
            clusterAvailable = true;
            
            System.out.println("✅ Connected to Kubernetes cluster: " + 
                    config.getMasterUrl());
        } catch (Exception e) {
            System.err.println("⚠️  No Kubernetes cluster available: " + e.getMessage());
            System.err.println("⚠️  Integration tests will be skipped");
            clusterAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @Order(1)
    void testClusterConnection() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        assertNotNull(client);
        assertNotNull(collector);
        System.out.println("✅ Cluster connection test passed");
    }

    @Test
    @Order(2)
    void testListNamespaces() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        List<String> namespaces = collector.listNamespaces();
        
        assertNotNull(namespaces);
        assertFalse(namespaces.isEmpty(), "Should have at least one namespace");
        
        System.out.println("\n📋 Found namespaces:");
        for (String ns : namespaces) {
            System.out.println("  - " + ns);
        }
        
        // Common namespaces that should exist
        assertTrue(namespaces.contains("default") || 
                   namespaces.contains("kube-system"),
                   "Should contain default or kube-system namespace");
    }

    @Test
    @Order(3)
    void testCollectDefaultNamespace() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        FlatNamespaceModel model = collector.collectNamespace("default", "test-cluster");
        
        assertNotNull(model);
        assertEquals("default", model.getName());
        assertEquals("test-cluster", model.getClusterName());
        assertNotNull(model.getObjects());
        
        System.out.println("\n📦 Collected from 'default' namespace:");
        System.out.println("  Total objects: " + model.getObjects().size());
        
        // Print summary by kind
        Map<String, Integer> kindCounts = new java.util.HashMap<>();
        for (FlatObjectModel obj : model.getObjects().values()) {
            kindCounts.merge(obj.getKind(), 1, Integer::sum);
        }
        
        System.out.println("\n  By kind:");
        kindCounts.forEach((kind, count) -> 
            System.out.println("    " + kind + ": " + count));
    }

    @Test
    @Order(4)
    void testCollectKubeSystemNamespace() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        FlatNamespaceModel model = collector.collectNamespace("kube-system", "test-cluster");
        
        assertNotNull(model);
        assertEquals("kube-system", model.getName());
        assertNotNull(model.getObjects());
        
        System.out.println("\n📦 Collected from 'kube-system' namespace:");
        System.out.println("  Total objects: " + model.getObjects().size());
        
        // kube-system should have some system components
        assertTrue(model.getObjects().size() > 0, 
                "kube-system should contain objects");
    }

    @Test
    @Order(5)
    void testCollectSpecificKinds() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        List<String> kinds = Arrays.asList("Deployment", "Service", "ConfigMap");
        FlatNamespaceModel model = collector.collectNamespaceByKinds(
                "kube-system", "test-cluster", kinds);
        
        assertNotNull(model);
        assertEquals("kube-system", model.getName());
        
        System.out.println("\n📦 Collected specific kinds from 'kube-system':");
        System.out.println("  Requested kinds: " + kinds);
        System.out.println("  Total objects: " + model.getObjects().size());
        
        // Verify only requested kinds are present
        for (FlatObjectModel obj : model.getObjects().values()) {
            assertTrue(kinds.contains(obj.getKind()),
                    "Object kind should be in requested kinds list");
        }
    }

    @Test
    @Order(6)
    void testFlatObjectModelStructure() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        FlatNamespaceModel nsModel = collector.collectNamespace("kube-system", "test-cluster");
        
        if (nsModel.getObjects().isEmpty()) {
            System.out.println("⚠️  No objects found in kube-system");
            return;
        }
        
        // Get first object to inspect
        FlatObjectModel firstObject = nsModel.getObjects().values().iterator().next();
        
        System.out.println("\n🔍 Inspecting first object:");
        System.out.println("  Kind: " + firstObject.getKind());
        System.out.println("  Name: " + firstObject.getName());
        System.out.println("  Namespace: " + firstObject.getNamespace());
        System.out.println("  API Version: " + firstObject.getApiVersion());
        
        assertNotNull(firstObject.getKind());
        assertNotNull(firstObject.getName());
        assertNotNull(firstObject.getNamespace());
        
        if (firstObject.getMetadata() != null && !firstObject.getMetadata().isEmpty()) {
            System.out.println("\n  Metadata fields: " + firstObject.getMetadata().size());
            System.out.println("  Sample metadata:");
            firstObject.getMetadata().entrySet().stream()
                    .limit(5)
                    .forEach(entry -> 
                        System.out.println("    " + entry.getKey() + ": " + entry.getValue()));
        }
        
        if (firstObject.getSpec() != null && !firstObject.getSpec().isEmpty()) {
            System.out.println("\n  Spec fields: " + firstObject.getSpec().size());
            System.out.println("  Sample spec:");
            firstObject.getSpec().entrySet().stream()
                    .limit(5)
                    .forEach(entry -> 
                        System.out.println("    " + entry.getKey() + ": " + entry.getValue()));
        }
    }

    @Test
    @Order(7)
    void testGetObjectsByKind() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        FlatNamespaceModel model = collector.collectNamespace("kube-system", "test-cluster");
        
        Map<String, FlatObjectModel> services = model.getObjectsByKind("Service");
        Map<String, FlatObjectModel> deployments = model.getObjectsByKind("Deployment");
        
        System.out.println("\n📊 Objects by kind:");
        System.out.println("  Services: " + services.size());
        System.out.println("  Deployments: " + deployments.size());
        
        assertNotNull(services);
        assertNotNull(deployments);
    }

    @Test
    @Order(8)
    void testGetAllFields() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        FlatNamespaceModel nsModel = collector.collectNamespace("default", "test-cluster");
        
        if (nsModel.getObjects().isEmpty()) {
            System.out.println("⚠️  No objects found in default namespace");
            return;
        }
        
        FlatObjectModel obj = nsModel.getObjects().values().iterator().next();
        Map<String, String> allFields = obj.getAllFields();
        
        System.out.println("\n🗂️  All fields (metadata + spec):");
        System.out.println("  Total fields: " + allFields.size());
        
        assertNotNull(allFields);
        
        // Show sample fields
        if (!allFields.isEmpty()) {
            System.out.println("  Sample fields:");
            allFields.entrySet().stream()
                    .limit(10)
                    .forEach(entry -> 
                        System.out.println("    " + entry.getKey() + ": " + entry.getValue()));
        }
    }

    @Test
    @Order(9)
    void testCollectMultipleNamespaces() {
        if (!clusterAvailable) {
            System.out.println("⚠️  Skipping test - no cluster available");
            return;
        }
        
        List<String> namespaces = collector.listNamespaces();
        
        System.out.println("\n📚 Collecting from multiple namespaces:");
        
        int totalObjects = 0;
        for (String ns : namespaces.stream().limit(3).toArray(String[]::new)) {
            FlatNamespaceModel model = collector.collectNamespace(ns, "test-cluster");
            int objectCount = model.getObjects().size();
            totalObjects += objectCount;
            
            System.out.println("  " + ns + ": " + objectCount + " objects");
        }
        
        System.out.println("  Total: " + totalObjects + " objects");
        assertTrue(totalObjects >= 0);
    }
}
