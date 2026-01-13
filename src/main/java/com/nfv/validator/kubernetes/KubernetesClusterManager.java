package com.nfv.validator.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for multiple Kubernetes cluster connections.
 * Thread-safe for parallel batch execution.
 */
@Slf4j
@ApplicationScoped
public class KubernetesClusterManager {
    
    private final Map<String, KubernetesClient> clients = new ConcurrentHashMap<>();
    private volatile KubernetesClient defaultClient;
    
    public KubernetesClusterManager() {
        // Lazy initialization - do NOT create client in constructor to avoid Quarkus classloader issues
        log.info("KubernetesClusterManager initialized (client will be created on first use)");
    }
    
    private synchronized KubernetesClient getDefaultClient() {
        if (defaultClient == null) {
            Config config = new ConfigBuilder()
                    .withConnectionTimeout(10000)
                    .withRequestTimeout(30000)
                    .build();
            defaultClient = new DefaultKubernetesClient(config);
            clients.put("current", defaultClient);
            log.info("Initialized default Kubernetes client from current context with 10s connection timeout");
        }
        return defaultClient;
    }
    
    /**
     * Get client for a specific cluster name
     * Returns default client if cluster name is "current" or not found
     */
    public KubernetesClient getClient(String clusterName) {
        if (clusterName == null || clusterName.equals("current")) {
            return getDefaultClient();
        }
        
        return clients.computeIfAbsent(clusterName, name -> {
            try {
                // Try to load from kubeconfig with specific context
                // Note: This attempts to use the context name, may fall back to default
                Config config = Config.autoConfigure(name);
                KubernetesClient client = new DefaultKubernetesClient(config);
                log.info("Created client for cluster: {}", name);
                return client;
            } catch (Exception e) {
                log.warn("Failed to create client for cluster {}, using default: {}", name, e.getMessage());
                return getDefaultClient();
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
