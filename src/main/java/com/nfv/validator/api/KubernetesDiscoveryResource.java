package com.nfv.validator.api;

import com.nfv.validator.kubernetes.K8sDataCollector;
import com.nfv.validator.kubernetes.KubeConfigReader;
import com.nfv.validator.kubernetes.KubernetesClusterManager;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoints for Kubernetes cluster/namespace discovery.
 */
@Slf4j
@Path("/kvalidator/api/kubernetes")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class KubernetesDiscoveryResource {

    @Inject
    KubernetesClusterManager clusterManager;

    @Inject
    KubeConfigReader kubeConfigReader;

    @GET
    @Path("/clusters")
    public Response listClusters() {
        List<String> clusters = kubeConfigReader.listContexts();
        return Response.ok(clusters).build();
    }

    @GET
    @Path("/namespaces")
    public Response listNamespaces(@QueryParam("cluster") String cluster) {
        String clusterName = (cluster == null || cluster.isBlank()) ? "current" : cluster;
        try {
            K8sDataCollector collector = new K8sDataCollector(clusterManager.getClient(clusterName));
            List<String> namespaces = collector.listNamespaces();
            Collections.sort(namespaces);
            return Response.ok(namespaces).build();
        } catch (Exception e) {
            log.error("Failed to list namespaces for cluster {}", clusterName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to list namespaces\"}")
                    .build();
        }
    }

    /**
     * Search namespaces across all clusters by keyword
     */
    @GET
    @Path("/namespaces/search")
    public Response searchNamespaces(@QueryParam("keyword") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"success\": false, \"error\": \"Keyword is required\"}")
                    .build();
        }

        try {
            List<String> clusters = kubeConfigReader.listContexts();
            List<Map<String, Object>> results = new ArrayList<>();
            String searchKeyword = keyword.toLowerCase().trim();

            for (String cluster : clusters) {
                try {
                    K8sDataCollector collector = new K8sDataCollector(clusterManager.getClient(cluster));
                    List<String> namespaces = collector.listNamespaces();
                    
                    // Filter namespaces that match the keyword
                    List<String> matchedNamespaces = namespaces.stream()
                            .filter(ns -> ns.toLowerCase().contains(searchKeyword))
                            .collect(Collectors.toList());

                    for (String namespace : matchedNamespaces) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("cluster", cluster);
                        result.put("namespace", namespace);
                        
                        // Try to get object count for this namespace
                        try {
                            int objectCount = collector.getResourceCount(namespace);
                            result.put("objectCount", objectCount);
                        } catch (Exception e) {
                            log.debug("Could not get object count for {}/{}", cluster, namespace);
                            result.put("objectCount", 0);
                        }
                        
                        result.put("description", String.format("Namespace in cluster %s", cluster));
                        results.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Failed to search namespaces in cluster {}", cluster, e);
                    // Continue with next cluster
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", results);
            response.put("message", String.format("Found %d matching namespace(s)", results.size()));

            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to search namespaces", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"success\": false, \"error\": \"Failed to search namespaces\"}")
                    .build();
        }
    }
}

