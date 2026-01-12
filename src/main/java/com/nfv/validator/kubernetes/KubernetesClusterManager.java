package com.nfv.validator.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager for multiple Kubernetes cluster connections
 */
@Slf4j
public class KubernetesClusterManager {
    
    private Map<String, KubernetesClient> clients = new HashMap<>();
    private KubernetesClient defaultClient;
    
    public KubernetesClusterManager() {
        // Initialize default client from current context
        this.defaultClient = new KubernetesClientBuilder().build();
        this.clients.put("current", defaultClient);
        log.info("Initialized default Kubernetes client from current context");
    }
    
    /**
     * Get client for a specific cluster name
     * Returns default client if cluster name is "current" or not found
     */
    public KubernetesClient getClient(String clusterName) {
        if (clusterName == null || clusterName.equals("current")) {
            return defaultClient;
        }
        
        return clients.computeIfAbsent(clusterName, name -> {
            try {
                // Try to load from kubeconfig with specific context
                // Note: This attempts to use the context name, may fall back to default
                Config config = Config.autoConfigure(name);
                KubernetesClient client = new KubernetesClientBuilder()
                        .withConfig(config)
                        .build();
                log.info("Created client for cluster: {}", name);
                return client;
            } catch (Exception e) {
                log.warn("Failed to create client for cluster {}, using default: {}", name, e.getMessage());
                return defaultClient;
            }
        });
    }
    
    /**
     * Add a client for a specific cluster
     */
    public void addClient(String clusterName, KubernetesClient client) {
        clients.put(clusterName, client);
        log.info("Added client for cluster: {}", clusterName);
    }
    
    /**
     * Get the default client
     */
    public KubernetesClient getDefaultClient() {
        return defaultClient;
    }
    
    /**
     * Close all clients
     */
    public void closeAll() {
        clients.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing client: {}", e.getMessage());
            }
        });
        clients.clear();
        log.info("Closed all Kubernetes clients");
    }
}
