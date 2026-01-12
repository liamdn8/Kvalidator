package com.nfv.validator.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Collects Kubernetes resources and converts them to FlatObjectModel and FlatNamespaceModel
 */
@Slf4j
public class K8sDataCollector {

    private final KubernetesClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public K8sDataCollector(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Collect all resources from a namespace and convert to FlatNamespaceModel
     * 
     * @param namespace namespace name
     * @param clusterName cluster identifier
     * @return FlatNamespaceModel
     */
    public FlatNamespaceModel collectNamespace(String namespace, String clusterName) {
        log.info("Collecting namespace '{}' from cluster '{}'", namespace, clusterName);
        
        FlatNamespaceModel model = new FlatNamespaceModel();
        model.setName(namespace);
        model.setClusterName(clusterName);
        model.setObjects(new HashMap<>());

        // Collect Deployments
        List<Deployment> deployments = client.apps().deployments()
                .inNamespace(namespace).list().getItems();
        for (Deployment deployment : deployments) {
            FlatObjectModel flatObj = convertToFlatObjectModel(deployment);
            model.addObject(deployment.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} Deployments", deployments.size());

        // Collect StatefulSets
        List<StatefulSet> statefulSets = client.apps().statefulSets()
                .inNamespace(namespace).list().getItems();
        for (StatefulSet statefulSet : statefulSets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(statefulSet);
            model.addObject(statefulSet.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} StatefulSets", statefulSets.size());

        // Collect DaemonSets
        List<DaemonSet> daemonSets = client.apps().daemonSets()
                .inNamespace(namespace).list().getItems();
        for (DaemonSet daemonSet : daemonSets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(daemonSet);
            model.addObject(daemonSet.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} DaemonSets", daemonSets.size());

        // Collect Services
        List<Service> services = client.services()
                .inNamespace(namespace).list().getItems();
        for (Service service : services) {
            FlatObjectModel flatObj = convertToFlatObjectModel(service);
            model.addObject(service.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} Services", services.size());

        // Collect ConfigMaps
        List<ConfigMap> configMaps = client.configMaps()
                .inNamespace(namespace).list().getItems();
        for (ConfigMap configMap : configMaps) {
            FlatObjectModel flatObj = convertToFlatObjectModel(configMap);
            model.addObject(configMap.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} ConfigMaps", configMaps.size());

        // Collect Secrets
        List<Secret> secrets = client.secrets()
                .inNamespace(namespace).list().getItems();
        for (Secret secret : secrets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(secret);
            model.addObject(secret.getMetadata().getName(), flatObj);
        }
        log.debug("Collected {} Secrets", secrets.size());

        // Collect Pods
        // List<Pod> pods = client.pods()
        //         .inNamespace(namespace).list().getItems();
        // for (Pod pod : pods) {
        //     FlatObjectModel flatObj = convertToFlatObjectModel(pod);
        //     model.addObject(pod.getMetadata().getName(), flatObj);
        // }
        // log.debug("Collected {} Pods", pods.size());

        log.info("Collected total {} objects from namespace '{}'", 
                model.getObjects().size(), namespace);
        
        return model;
    }

    /**
     * Convert a Kubernetes HasMetadata object to FlatObjectModel
     * 
     * @param kubernetesObject any Kubernetes object
     * @return FlatObjectModel
     */
    public FlatObjectModel convertToFlatObjectModel(HasMetadata kubernetesObject) {
        FlatObjectModel model = new FlatObjectModel();
        
        // Set basic fields
        model.setKind(kubernetesObject.getKind());
        model.setApiVersion(kubernetesObject.getApiVersion());
        model.setName(kubernetesObject.getMetadata().getName());
        model.setNamespace(kubernetesObject.getMetadata().getNamespace());

        // Flatten and set metadata
        Map<String, String> flattenedMetadata = flattenMetadata(kubernetesObject.getMetadata());
        model.setMetadata(flattenedMetadata);

        // Flatten and set spec
        Map<String, String> flattenedSpec = flattenSpec(kubernetesObject);
        model.setSpec(flattenedSpec);

        return model;
    }

    /**
     * Flatten Kubernetes object metadata
     */
    private Map<String, String> flattenMetadata(ObjectMeta metadata) {
        Map<String, String> flattened = new HashMap<>();
        
        if (metadata == null) return flattened;

        // Basic metadata fields (store without prefix - it will be added by getAllFields())
        if (metadata.getName() != null) {
            flattened.put("name", metadata.getName());
        }
        if (metadata.getNamespace() != null) {
            flattened.put("namespace", metadata.getNamespace());
        }

        // Labels
        if (metadata.getLabels() != null) {
            for (Map.Entry<String, String> label : metadata.getLabels().entrySet()) {
                flattened.put("labels." + label.getKey(), label.getValue());
            }
        }

        // Annotations
        if (metadata.getAnnotations() != null) {
            for (Map.Entry<String, String> annotation : metadata.getAnnotations().entrySet()) {
                flattened.put("annotations." + annotation.getKey(), annotation.getValue());
            }
        }

        return flattened;
    }

    /**
     * Flatten Kubernetes object spec
     */
    private Map<String, String> flattenSpec(HasMetadata kubernetesObject) {
        Map<String, String> flattened = new HashMap<>();

        try {
            // Convert object to JsonNode
            JsonNode node = objectMapper.valueToTree(kubernetesObject);
            
            // Extract spec node (store without prefix - it will be added by getAllFields())
            JsonNode specNode = node.get("spec");
            if (specNode != null) {
                flattenJsonNode("", specNode, flattened);
            }
        } catch (Exception e) {
            log.error("Failed to flatten spec for {}: {}", 
                    kubernetesObject.getKind(), e.getMessage());
        }

        return flattened;
    }

    /**
     * Recursively flatten a JsonNode
     */
    private void flattenJsonNode(String currentPath, JsonNode node, Map<String, String> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                String newPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;
                flattenJsonNode(newPath, field.getValue(), result);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String newPath = currentPath + "[" + i + "]";
                flattenJsonNode(newPath, node.get(i), result);
            }
        } else {
            // Leaf node - store the value
            result.put(currentPath, node.asText());
        }
    }

    /**
     * Collect specific resource types from a namespace
     * 
     * @param namespace namespace name
     * @param clusterName cluster identifier
     * @param kinds list of resource kinds to collect (e.g., "Deployment", "Service")
     * @return FlatNamespaceModel
     */
    public FlatNamespaceModel collectNamespaceByKinds(String namespace, String clusterName, 
                                                       List<String> kinds) {
        log.info("Collecting specific resources {} from namespace '{}'", kinds, namespace);
        
        FlatNamespaceModel model = new FlatNamespaceModel();
        model.setName(namespace);
        model.setClusterName(clusterName);
        model.setObjects(new HashMap<>());

        for (String kind : kinds) {
            switch (kind) {
                case "Deployment":
                    collectDeployments(namespace, model);
                    break;
                case "StatefulSet":
                    collectStatefulSets(namespace, model);
                    break;
                case "DaemonSet":
                    collectDaemonSets(namespace, model);
                    break;
                case "Service":
                    collectServices(namespace, model);
                    break;
                case "ConfigMap":
                    collectConfigMaps(namespace, model);
                    break;
                case "Secret":
                    collectSecrets(namespace, model);
                    break;
                case "Pod":
                    collectPods(namespace, model);
                    break;
                default:
                    log.warn("Unsupported resource kind: {}", kind);
            }
        }

        log.info("Collected {} objects from namespace '{}'", 
                model.getObjects().size(), namespace);
        
        return model;
    }

    private void collectDeployments(String namespace, FlatNamespaceModel model) {
        List<Deployment> deployments = client.apps().deployments()
                .inNamespace(namespace).list().getItems();
        for (Deployment deployment : deployments) {
            FlatObjectModel flatObj = convertToFlatObjectModel(deployment);
            model.addObject(deployment.getMetadata().getName(), flatObj);
        }
    }

    private void collectStatefulSets(String namespace, FlatNamespaceModel model) {
        List<StatefulSet> statefulSets = client.apps().statefulSets()
                .inNamespace(namespace).list().getItems();
        for (StatefulSet statefulSet : statefulSets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(statefulSet);
            model.addObject(statefulSet.getMetadata().getName(), flatObj);
        }
    }

    private void collectDaemonSets(String namespace, FlatNamespaceModel model) {
        List<DaemonSet> daemonSets = client.apps().daemonSets()
                .inNamespace(namespace).list().getItems();
        for (DaemonSet daemonSet : daemonSets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(daemonSet);
            model.addObject(daemonSet.getMetadata().getName(), flatObj);
        }
    }

    private void collectServices(String namespace, FlatNamespaceModel model) {
        List<Service> services = client.services()
                .inNamespace(namespace).list().getItems();
        for (Service service : services) {
            FlatObjectModel flatObj = convertToFlatObjectModel(service);
            model.addObject(service.getMetadata().getName(), flatObj);
        }
    }

    private void collectConfigMaps(String namespace, FlatNamespaceModel model) {
        List<ConfigMap> configMaps = client.configMaps()
                .inNamespace(namespace).list().getItems();
        for (ConfigMap configMap : configMaps) {
            FlatObjectModel flatObj = convertToFlatObjectModel(configMap);
            model.addObject(configMap.getMetadata().getName(), flatObj);
        }
    }

    private void collectSecrets(String namespace, FlatNamespaceModel model) {
        List<Secret> secrets = client.secrets()
                .inNamespace(namespace).list().getItems();
        for (Secret secret : secrets) {
            FlatObjectModel flatObj = convertToFlatObjectModel(secret);
            model.addObject(secret.getMetadata().getName(), flatObj);
        }
    }

    private void collectPods(String namespace, FlatNamespaceModel model) {
        List<Pod> pods = client.pods()
                .inNamespace(namespace).list().getItems();
        for (Pod pod : pods) {
            FlatObjectModel flatObj = convertToFlatObjectModel(pod);
            model.addObject(pod.getMetadata().getName(), flatObj);
        }
    }

    /**
     * List all namespaces in the cluster
     * 
     * @return list of namespace names
     */
    public List<String> listNamespaces() {
        List<String> namespaces = new ArrayList<>();
        List<Namespace> nsList = client.namespaces().list().getItems();
        for (Namespace ns : nsList) {
            namespaces.add(ns.getMetadata().getName());
        }
        log.info("Found {} namespaces", namespaces.size());
        return namespaces;
    }
}
