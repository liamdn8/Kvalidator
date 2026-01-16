package com.nfv.validator.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.kubernetes.api.model.NamedContext;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
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
     * Get client for a specific cluster name/context
     * If clusterName is "current" or null, returns default client
     * Otherwise, attempts to load the specified context from kubeconfig
     * 
     * NOTE: The vimName in CNF Checklist JSON must match a context name in your kubeconfig.
     * Use 'kubectl config get-contexts' to see available contexts.
     */
    public KubernetesClient getClient(String clusterName) {
        if (clusterName == null || clusterName.equals("current")) {
            return getDefaultClient();
        }
        
        return clients.computeIfAbsent(clusterName, contextName -> {
            try {
                // Load kubeconfig and find the specified context
                File kubeConfigFile = getKubeConfigFile();
                io.fabric8.kubernetes.api.model.Config kubeConfig = KubeConfigUtils.parseConfig(kubeConfigFile);
                
                // Check if the context exists in kubeconfig
                NamedContext namedContext = null;
                for (NamedContext ctx : kubeConfig.getContexts()) {
                    if (ctx.getName().equals(contextName)) {
                        namedContext = ctx;
                        break;
                    }
                }
                
                if (namedContext == null) {
                    // List available contexts to help user
                    java.util.List<String> availableContexts = new java.util.ArrayList<>();
                    for (NamedContext ctx : kubeConfig.getContexts()) {
                        availableContexts.add(ctx.getName());
                    }
                    
                    log.error("❌ Context '{}' not found in kubeconfig!", contextName);
                    log.error("Available contexts: {}", availableContexts);
                    log.warn("⚠️  Falling back to default context. Please ensure vimName matches a context name in kubeconfig.");
                    return getDefaultClient();
                }
                
                // Build config with the specific context
                Config config = Config.autoConfigure(contextName);
                config.setConnectionTimeout(10000);
                config.setRequestTimeout(30000);
                
                KubernetesClient client = new DefaultKubernetesClient(config);
                log.info("✓ Successfully created client for vimName/context '{}' -> cluster: {}", 
                        contextName, namedContext.getContext().getCluster());
                return client;
                
            } catch (Exception e) {
                log.error("Failed to create client for vimName/context '{}': {}", contextName, e.getMessage(), e);
                log.warn("Falling back to default context");
                return getDefaultClient();
            }
        });
    }
    
    /**
     * Get the kubeconfig file location
     */
    private File getKubeConfigFile() {
        String kubeConfigPath = System.getenv("KUBECONFIG");
        if (kubeConfigPath == null || kubeConfigPath.isEmpty()) {
            kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        }
        return new File(kubeConfigPath);
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
