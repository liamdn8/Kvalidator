package com.nfv.validator.api;

import com.nfv.validator.adapter.SemanticToFlatAdapter;
import com.nfv.validator.comparison.NamespaceComparatorV2;
import com.nfv.validator.config.FeatureFlags;
import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.kubernetes.K8sDataCollectorV2;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.service.ValidationServiceV2;
import com.nfv.validator.yaml.YamlDataCollectorV2;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * API endpoint để test V2 Semantic Comparison
 */
@Path("/api/v2")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ValidationV2Resource {

    @Inject
    KubernetesClusterManager clusterManager;

    /**
     * Check V2 status
     */
    @GET
    @Path("/status")
    public Response getV2Status() {
        FeatureFlags flags = FeatureFlags.getInstance();
        
        Map<String, Object> status = new HashMap<>();
        status.put("v2Enabled", flags.isUseSemanticComparison());
        status.put("verboseLogging", flags.isVerboseSemanticLogging());
        status.put("convertToFlat", flags.isConvertToFlatFormat());
        status.put("version", "2.0.0");
        status.put("engine", "Semantic Comparison");
        
        log.info("[V2 API] Status checked - enabled: {}", flags.isUseSemanticComparison());
        
        return Response.ok(status).build();
    }

    /**
     * Enable/disable V2
     */
    @POST
    @Path("/toggle")
    public Response toggleV2(Map<String, Boolean> request) {
        Boolean enable = request.get("enabled");
        if (enable == null) {
            return Response.status(400).entity("Missing 'enabled' field").build();
        }
        
        FeatureFlags flags = FeatureFlags.getInstance();
        flags.setUseSemanticComparison(enable);
        
        log.info("[V2 API] V2 comparison {}", enable ? "ENABLED" : "DISABLED");
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("v2Enabled", enable);
        response.put("message", "V2 semantic comparison " + (enable ? "enabled" : "disabled"));
        
        return Response.ok(response).build();
    }

    /**
     * Test V2 comparison với 2 clusters
     */
    @POST
    @Path("/compare/clusters")
    public Response compareClustersV2(Map<String, String> request) {
        try {
            String leftCluster = request.get("leftCluster");
            String rightCluster = request.get("rightCluster");
            String namespace = request.get("namespace");
            
            if (leftCluster == null || rightCluster == null || namespace == null) {
                return Response.status(400).entity("Missing required fields").build();
            }
            
            log.info("[V2 API] Comparing {}@{} vs {}@{}", 
                    namespace, leftCluster, namespace, rightCluster);
            
            KubernetesClient leftClient = clusterManager.getClient(leftCluster);
            KubernetesClient rightClient = clusterManager.getClient(rightCluster);
            
            NamespaceComparison result = ValidationServiceV2.compareNamespacesV2(
                    leftClient, rightClient,
                    namespace, namespace,
                    leftCluster, rightCluster,
                    null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("engine", "V2-Semantic");
            response.put("leftNamespace", result.getLeftNamespace());
            response.put("rightNamespace", result.getRightNamespace());
            response.put("objectsCompared", result.getAllObjectResults().size());
            response.put("differencesFound", result.getDifferenceCount());
            response.put("summary", result.getSummary());
            response.put("objectsWithDifferences", result.getObjectsWithDifferences().size());
            
            log.info("[V2 API] Comparison complete - {} objects, {} differences", 
                    result.getAllObjectResults().size(), result.getDifferenceCount());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            log.error("[V2 API] Comparison failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return Response.status(500).entity(error).build();
        }
    }

    /**
     * Test V2 comparison với baseline YAML
     */
    @POST
    @Path("/compare/baseline")
    public Response compareWithBaselineV2(Map<String, String> request) {
        try {
            String baselinePath = request.get("baselinePath");
            String cluster = request.get("cluster");
            String namespace = request.get("namespace");
            
            if (baselinePath == null || cluster == null || namespace == null) {
                return Response.status(400).entity("Missing required fields").build();
            }
            
            log.info("[V2 API] Comparing baseline '{}' vs {}@{}", 
                    baselinePath, namespace, cluster);
            
            KubernetesClient client = clusterManager.getClient(cluster);
            
            NamespaceComparison result = ValidationServiceV2.compareBaselineWithRuntimeV2(
                    baselinePath,
                    "baseline",
                    client,
                    namespace,
                    cluster,
                    null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("engine", "V2-Semantic-Baseline");
            response.put("baselinePath", baselinePath);
            response.put("runtimeNamespace", namespace + "@" + cluster);
            response.put("objectsCompared", result.getAllObjectResults().size());
            response.put("differencesFound", result.getDifferenceCount());
            response.put("summary", result.getSummary());
            response.put("differences", result.getObjectsWithDifferences());
            
            log.info("[V2 API] Baseline comparison complete - {} objects, {} differences", 
                    result.getAllObjectResults().size(), result.getDifferenceCount());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            log.error("[V2 API] Baseline comparison failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return Response.status(500).entity(error).build();
        }
    }

    /**
     * Demo V2 với containers trong thứ tự khác nhau
     */
    @GET
    @Path("/demo/order-independence")
    public Response demoOrderIndependence() {
        try {
            log.info("[V2 API] Running order-independence demo");
            
            // Tạo 2 semantic models giống nhau nhưng containers khác thứ tự
            SemanticNamespaceModel baseline = createDemoNamespace("baseline", 
                    new String[]{"nginx", "sidecar"});
            SemanticNamespaceModel runtime = createDemoNamespace("runtime", 
                    new String[]{"sidecar", "nginx"}); // Reverse order!
            
            NamespaceComparison result = NamespaceComparatorV2.compareNamespace(
                    baseline, runtime,
                    "Baseline", "Runtime (different order)",
                    null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("engine", "V2-Semantic-Demo");
            response.put("scenario", "Same containers, different order");
            response.put("baselineOrder", new String[]{"nginx", "sidecar"});
            response.put("runtimeOrder", new String[]{"sidecar", "nginx"});
            response.put("differencesFound", result.getDifferenceCount());
            response.put("expectedResult", "0 differences (order-independent)");
            response.put("actualResult", result.getDifferenceCount() == 0 ? "PASS ✅" : "FAIL ❌");
            response.put("message", result.getDifferenceCount() == 0 ? 
                    "V2 correctly matched containers by identity despite different order!" : 
                    "Unexpected differences found");
            
            log.info("[V2 API] Demo complete - {} differences found (expected: 0)", 
                    result.getDifferenceCount());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            log.error("[V2 API] Demo failed", e);
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Helper: Create demo namespace model
     */
    private SemanticNamespaceModel createDemoNamespace(String name, String[] containerNames) {
        SemanticNamespaceModel ns = new SemanticNamespaceModel();
        ns.setName(name);
        ns.setClusterName("demo");
        
        var obj = new com.nfv.validator.model.semantic.SemanticObjectModel();
        obj.setName("demo-app");
        obj.setKind("Deployment");
        obj.setMetadata(new HashMap<>());
        
        // Create containers list
        var containers = new java.util.ArrayList<Map<String, Object>>();
        for (String containerName : containerNames) {
            Map<String, Object> container = new HashMap<>();
            container.put("name", containerName);
            container.put("image", containerName + ":latest");
            container.put("cpu", "100m");
            containers.add(container);
        }
        
        // Build spec structure
        Map<String, Object> spec = new HashMap<>();
        Map<String, Object> template = new HashMap<>();
        Map<String, Object> templateSpec = new HashMap<>();
        templateSpec.put("containers", containers);
        template.put("spec", templateSpec);
        spec.put("template", template);
        obj.setSpec(spec);
        
        ns.addObject("demo-app", obj);
        return ns;
    }
}
