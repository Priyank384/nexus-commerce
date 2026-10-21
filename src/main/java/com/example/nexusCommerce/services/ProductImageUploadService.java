package com.example.nexusCommerce.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Stores product images on the local filesystem under ${app.upload.dir} and rewrites the
 * product's image column with the served URL (/uploads/products/{id}/<unique>.png). Swap
 * the storage path for S3 or similar by replacing this bean only.
 */
@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ProductRepository productRepository;

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDir;

    @Transactional
    public String upload(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidRequestException("file is required");
        if (file.getSize() > MAX_BYTES) throw new InvalidRequestException("image must be under 5 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw new InvalidRequestException("only PNG, JPEG and WebP images are accepted");
        }
        String filename = UUID.randomUUID() + extension(contentType);
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new InvalidRequestException("failed to store image: " + e.getMessage());
        }
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        String url = "/uploads/products/" + filename;
        product.setImage(url);
        productRepository.save(product);
        return url;
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
