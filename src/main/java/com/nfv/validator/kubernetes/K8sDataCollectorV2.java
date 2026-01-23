package com.nfv.validator.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nfv.validator.model.semantic.SemanticNamespaceModel;
import com.nfv.validator.model.semantic.SemanticObjectModel;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * V2 Data Collector - Collects Kubernetes resources in semantic structure
 * Preserves nested lists and objects for accurate semantic comparison
 * Does NOT flatten list items - keeps containers[], volumes[], env[] as structured lists
 */
@Slf4j
public class K8sDataCollectorV2 {

    private final KubernetesClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public K8sDataCollectorV2(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Collect all resources from a namespace in semantic structure
     */
    public SemanticNamespaceModel collectNamespace(String namespace, String clusterName) {
        log.info("[V2] Collecting namespace '{}' from cluster '{}' in semantic mode", namespace, clusterName);
        
        SemanticNamespaceModel model = new SemanticNamespaceModel();
        model.setName(namespace);
        model.setClusterName(clusterName);
        model.setObjects(new HashMap<>());

        // Collect Deployments
        try {
            List<Deployment> deployments = client.apps().deployments()
                    .inNamespace(namespace).list().getItems();
            for (Deployment deployment : deployments) {
                SemanticObjectModel semanticObj = convertToSemanticModel(deployment);
                model.addObject(deployment.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} Deployments", deployments.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect Deployments", e);
        }

        // Collect StatefulSets
        try {
            List<StatefulSet> statefulSets = client.apps().statefulSets()
                    .inNamespace(namespace).list().getItems();
            for (StatefulSet statefulSet : statefulSets) {
                SemanticObjectModel semanticObj = convertToSemanticModel(statefulSet);
                model.addObject(statefulSet.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} StatefulSets", statefulSets.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect StatefulSets", e);
        }

        // Collect DaemonSets
        try {
            List<DaemonSet> daemonSets = client.apps().daemonSets()
                    .inNamespace(namespace).list().getItems();
            for (DaemonSet daemonSet : daemonSets) {
                SemanticObjectModel semanticObj = convertToSemanticModel(daemonSet);
                model.addObject(daemonSet.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} DaemonSets", daemonSets.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect DaemonSets", e);
        }

        // Collect Services
        try {
            List<Service> services = client.services()
                    .inNamespace(namespace).list().getItems();
            for (Service service : services) {
                SemanticObjectModel semanticObj = convertToSemanticModel(service);
                model.addObject(service.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} Services", services.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect Services", e);
        }

        // Collect ConfigMaps
        try {
            List<ConfigMap> configMaps = client.configMaps()
                    .inNamespace(namespace).list().getItems();
            for (ConfigMap configMap : configMaps) {
                SemanticObjectModel semanticObj = convertToSemanticModel(configMap);
                model.addObject(configMap.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} ConfigMaps", configMaps.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect ConfigMaps", e);
        }

        // Collect Secrets
        try {
            List<Secret> secrets = client.secrets()
                    .inNamespace(namespace).list().getItems();
            for (Secret secret : secrets) {
                SemanticObjectModel semanticObj = convertToSemanticModel(secret);
                model.addObject(secret.getMetadata().getName(), semanticObj);
            }
            log.info("[V2] Collected {} Secrets", secrets.size());
        } catch (Exception e) {
            log.error("[V2] Failed to collect Secrets", e);
        }

        log.info("[V2] Collected total {} objects from namespace '{}'", 
                model.getObjectCount(), namespace);
        
        return model;
    }

    /**
     * Convert Kubernetes object to SemanticObjectModel (preserves nested structure)
     */
    public SemanticObjectModel convertToSemanticModel(HasMetadata kubernetesObject) {
        SemanticObjectModel model = new SemanticObjectModel();
        
        // Set basic fields
        model.setKind(kubernetesObject.getKind());
        model.setApiVersion(kubernetesObject.getApiVersion());
        model.setName(kubernetesObject.getMetadata().getName());
        model.setNamespace(kubernetesObject.getMetadata().getNamespace());

        // Convert metadata to nested structure
        Map<String, Object> metadata = extractMetadata(kubernetesObject.getMetadata());
        model.setMetadata(metadata);

        // Convert spec to nested structure (preserves lists!)
        Map<String, Object> spec = extractSpec(kubernetesObject);
        model.setSpec(spec);
        
        // Extract data separately (for ConfigMap, Secret)
        Map<String, Object> data = extractData(kubernetesObject);
        model.setData(data.isEmpty() ? null : data);

        return model;
    }

    /**
     * Extract metadata as nested structure
     */
    private Map<String, Object> extractMetadata(ObjectMeta metadata) {
        Map<String, Object> result = new HashMap<>();
        
        if (metadata == null) return result;

        if (metadata.getName() != null) {
            result.put("name", metadata.getName());
        }
        if (metadata.getNamespace() != null) {
            result.put("namespace", metadata.getNamespace());
        }

        // Labels as nested map
        if (metadata.getLabels() != null && !metadata.getLabels().isEmpty()) {
            result.put("labels", new HashMap<>(metadata.getLabels()));
        }

        // Annotations as nested map
        if (metadata.getAnnotations() != null && !metadata.getAnnotations().isEmpty()) {
            result.put("annotations", new HashMap<>(metadata.getAnnotations()));
        }

        return result;
    }

    /**
     * Extract spec as nested structure (PRESERVES LISTS)
     * This is the key difference from V1 - lists stay as lists
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSpec(HasMetadata kubernetesObject) {
        try {
            // Convert to JsonNode
            JsonNode node = objectMapper.valueToTree(kubernetesObject);
            
            // Extract spec only
            JsonNode specNode = node.get("spec");
            if (specNode != null) {
                Object converted = convertJsonNodeToMap(specNode);
                if (converted instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) converted;
                    return map;
                }
            }
            
        } catch (Exception e) {
            log.error("[V2] Failed to extract spec for {}: {}", 
                    kubernetesObject.getKind(), e.getMessage());
        }

        return new HashMap<>();
    }
    
    /**
     * Extract data separately (for ConfigMap, Secret, etc.)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(HasMetadata kubernetesObject) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Convert to JsonNode
            JsonNode node = objectMapper.valueToTree(kubernetesObject);
            
            // Extract data node (for ConfigMap, Secret)
            JsonNode dataNode = node.get("data");
            if (dataNode != null) {
                Object converted = convertJsonNodeToMap(dataNode);
                if (converted instanceof Map) {
                    result.putAll((Map<String, Object>) converted);
                }
            }
            
            // Extract binaryData node
            JsonNode binaryDataNode = node.get("binaryData");
            if (binaryDataNode != null) {
                Object converted = convertJsonNodeToMap(binaryDataNode);
                if (converted instanceof Map) {
                    result.put("binaryData", converted);
                }
            }
            
            // Extract stringData node (for Secret)
            JsonNode stringDataNode = node.get("stringData");
            if (stringDataNode != null) {
                Object converted = convertJsonNodeToMap(stringDataNode);
                if (converted instanceof Map) {
                    result.put("stringData", converted);
                }
            }
            
        } catch (Exception e) {
            log.error("[V2] Failed to extract data for {}: {}", 
                    kubernetesObject.getKind(), e.getMessage());
        }

        return result;
    }

    /**
     * Convert JsonNode to Map/List structure (preserves nested structure)
     * Key difference: Arrays become List<Map<String, Object>> instead of flattened keys
     */
    @SuppressWarnings("unchecked")
    private Object convertJsonNodeToMap(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                Object value = convertJsonNodeToMap(field.getValue());
                map.put(key, value);
            }
            return map;
        } else if (node.isArray()) {
            // Keep as list of objects - NO FLATTENING!
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                Object item = convertJsonNodeToMap(node.get(i));
                list.add(item);
            }
            return list;
        } else if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNumber()) {
            if (node.isInt() || node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else {
            return node.asText();
        }
    }

    /**
     * List all namespaces in the cluster
     */
    public List<String> listNamespaces() {
        List<String> namespaces = new ArrayList<>();
        List<Namespace> nsList = client.namespaces().list().getItems();
        for (Namespace ns : nsList) {
            namespaces.add(ns.getMetadata().getName());
        }
        log.info("[V2] Found {} namespaces", namespaces.size());
        return namespaces;
    }
}
