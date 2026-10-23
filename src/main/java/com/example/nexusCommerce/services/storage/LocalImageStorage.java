package com.example.nexusCommerce.services.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.nexusCommerce.exceptions.InvalidRequestException;

import lombok.extern.slf4j.Slf4j;

/**
 * Default development storage: writes under {@code app.upload.dir} and serves the files
 * through the {@code /uploads/products/**} resource handler. This is what boots when no
 * {@code STORAGE_TYPE} is set, so local runs and the test suite need no bucket.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    static final String URL_PREFIX = "/uploads/products/";

    private final Path directory;

    public LocalImageStorage(@Value("${app.upload.dir:uploads/products}") String uploadDir) {
        this.directory = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension, String contentType) {
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), content);
        } catch (IOException e) {
            throw new InvalidRequestException("failed to store image: " + e.getMessage());
        }
        return URL_PREFIX + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String filename = url.substring(URL_PREFIX.length());
        // A path segment only — anything with a separator or traversal is not ours.
        if (filename.isBlank() || filename.contains("/") || filename.contains("..")) return;
        try {
            Files.deleteIfExists(directory.resolve(filename));
        } catch (IOException e) {
            log.warn("Could not delete local image {}: {}", filename, e.getMessage());
        }
    }
}
