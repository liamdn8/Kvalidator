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
import java.util.Collections;
import java.util.List;

/**
 * REST endpoints for Kubernetes cluster/namespace discovery.
 */
@Slf4j
@Path("/api/kubernetes")
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
}
