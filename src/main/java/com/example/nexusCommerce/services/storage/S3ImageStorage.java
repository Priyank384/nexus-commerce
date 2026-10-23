package com.example.nexusCommerce.services.storage;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.nexusCommerce.exceptions.InvalidRequestException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3-compatible storage (Cloudflare R2 in production). Objects are named with UUIDs under a
 * key prefix and served from the bucket's public base URL, so the URL stored on a product
 * never changes and can be cached immutably.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

    private final S3Client client;
    private final String bucket;
    private final String keyPrefix;
    private final String publicBaseUrl;

    public S3ImageStorage(
            S3Client client,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.key-prefix:products}") String keyPrefix,
            @Value("${app.storage.s3.public-base-url:}") String publicBaseUrl) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(publicBaseUrl)) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=s3 requires S3_BUCKET and S3_PUBLIC_BASE_URL");
        }
        this.client = client;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix.replaceAll("^/+|/+$", "");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String store(byte[] content, String extension, String contentType) {
        String filename = UUID.randomUUID() + extension;
        String key = keyPrefix.isEmpty() ? filename : keyPrefix + "/" + filename;
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    // The key is a UUID, so the object behind this URL never changes.
                    .cacheControl("public, max-age=31536000, immutable")
                    .build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new InvalidRequestException("failed to store image: " + e.getMessage());
        }
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void delete(String url) {
        String prefix = publicBaseUrl + "/";
        if (url == null || !url.startsWith(prefix)) return;
        String key = url.substring(prefix.length());
        if (key.isBlank() || key.contains("..")) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Could not delete object {}: {}", key, e.getMessage());
        }
    }
}
