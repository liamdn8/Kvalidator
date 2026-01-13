package com.nfv.validator.service;

import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles persistent storage for uploaded baseline files.
 */
@Slf4j
@ApplicationScoped
public class BaselineStorageService {

    private static final Path UPLOAD_BASE_DIR = Paths.get("/tmp/.kvalidator/uploads");

    /**
     * Store the uploaded baseline file in a dedicated directory.
     *
     * @param tempFile         temporary file path created by RESTEasy
     * @param originalFilename filename supplied by the client
     * @return absolute path to the stored file
     * @throws IOException if the file cannot be persisted
     */
    public Path storeBaseline(Path tempFile, String originalFilename) throws IOException {
        if (tempFile == null) {
            throw new IOException("No temporary file provided");
        }

        Files.createDirectories(UPLOAD_BASE_DIR);

        String sanitizedName = sanitizeFilename(originalFilename);
        Path target = UPLOAD_BASE_DIR.resolve(sanitizedName);

        Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(tempFile);

        log.info("Stored baseline upload at {}", target);
        return target;
    }

    private String sanitizeFilename(String originalFilename) {
        String baseName = "baseline";
        String extension = "yaml";

        if (originalFilename != null && !originalFilename.isBlank()) {
            String trimmed = Paths.get(originalFilename.trim()).getFileName().toString();
            int lastDot = trimmed.lastIndexOf('.');
            if (lastDot > 0 && lastDot < trimmed.length() - 1) {
                baseName = trimmed.substring(0, lastDot).replaceAll("[^A-Za-z0-9_-]", "-");
                extension = trimmed.substring(lastDot + 1).replaceAll("[^A-Za-z0-9]", "");
            } else {
                baseName = trimmed.replaceAll("[^A-Za-z0-9_-]", "-");
            }
        }

        if (baseName.isBlank()) {
            baseName = "baseline";
        }
        String normalizedExt = extension.toLowerCase();
        if (normalizedExt.isBlank() || (!"yaml".equals(normalizedExt) && !"yml".equals(normalizedExt))) {
            extension = "yaml";
        } else {
            extension = normalizedExt;
        }

        String uniqueSuffix = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().replaceAll("-", "");
        return baseName + "-" + uniqueSuffix + "." + extension;
    }
}
