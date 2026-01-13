package com.nfv.validator.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight reader for kubeconfig contexts (cluster names).
 */
@Slf4j
@ApplicationScoped
public class KubeConfigReader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public List<String> listContexts() {
        Set<String> contexts = new LinkedHashSet<>();
        for (Path path : resolveKubeConfigPaths()) {
            try {
                JsonNode root = yamlMapper.readTree(path.toFile());
                JsonNode contextNode = root.path("contexts");
                if (contextNode.isArray()) {
                    for (JsonNode entry : contextNode) {
                        String name = entry.path("name").asText();
                        if (!name.isBlank()) {
                            contexts.add(name);
                        }
                    }
                }
                String current = root.path("current-context").asText();
                if (!current.isBlank()) {
                    contexts.add(current);
                }
            } catch (Exception e) {
                log.warn("Failed to read kubeconfig {}: {}", path, e.getMessage());
            }
        }

        if (contexts.isEmpty()) {
            contexts.add("current");
        }
        return new ArrayList<>(contexts);
    }

    private List<Path> resolveKubeConfigPaths() {
        List<Path> paths = new ArrayList<>();
        String kubeConfigEnv = System.getenv("KUBECONFIG");
        if (kubeConfigEnv != null && !kubeConfigEnv.isBlank()) {
            String[] entries = kubeConfigEnv.split(File.pathSeparator);
            for (String entry : entries) {
                Path path = Path.of(entry);
                if (Files.exists(path)) {
                    paths.add(path);
                }
            }
        }

        if (paths.isEmpty()) {
            Path defaultPath = Path.of(System.getProperty("user.home"), ".kube", "config");
            if (Files.exists(defaultPath)) {
                paths.add(defaultPath);
            }
        }
        return paths;
    }
}
