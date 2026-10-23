package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.GetProductImageResponseDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductImageRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductImage;
import com.example.nexusCommerce.services.storage.ImageStorage;

import lombok.RequiredArgsConstructor;

/**
 * Validates uploads and hands the bytes to the configured {@link ImageStorage} (local disk
 * by default, S3/R2 when STORAGE_TYPE=s3). The URL it returns is persisted verbatim on the
 * product and gallery rows.
 *
 * <p>V17 keeps one live row per (product, position), so reindexing moves every row to a
 * negative placeholder position and flushes before assigning the final 0..n-1 order. A
 * single pass would collide with the unique index halfway through a shuffle.
 */
@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductAdapter productAdapter;
    private final ImageStorage imageStorage;

    @Transactional
    @Caching(evict = { @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG,
            PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true) })
    public String upload(Long productId, MultipartFile file) {
        Product product = loadProduct(productId);
        String previousImage = product.getImage();
        String url = store(file);
        product.setImage(url);
        productRepository.save(product);

        // products.image is the historic mirror of position 0, so the gallery's first row
        // must follow it or the card and the PDP would disagree about the primary image.
        List<ProductImage> gallery = imageRepository.findByProductIdOrderByPositionAsc(productId);
        ProductImage primary = gallery.stream()
                .filter(image -> image.getPosition() == 0)
                .findFirst()
                .orElse(null);
        if (primary == null) {
            imageRepository.save(ProductImage.builder()
                    .product(product).url(url).position(0).alt(product.getTitle()).build());
        } else {
            primary.setUrl(url);
            imageRepository.save(primary);
        }
        // A gallery row may still point at the image the thumbnail was mirrored from; only
        // drop the object once nothing in this product references it any more.
        boolean stillReferenced = gallery.stream()
                .anyMatch(image -> Objects.equals(previousImage, image.getUrl()));
        if (!stillReferenced) {
            deleteAfterCommit(previousImage);
        }
        return url;
    }

    @Transactional
    @Caching(evict = { @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG,
            PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true) })
    public GetProductImageResponseDto appendGalleryImage(Long productId, MultipartFile file) {
        Product product = loadProduct(productId);
        String url = store(file);
        int nextPosition = imageRepository.findTopByProductIdOrderByPositionDesc(productId)
                .map(image -> image.getPosition() + 1)
                .orElse(0);
        ProductImage saved = imageRepository.save(ProductImage.builder()
                .product(product).url(url).position(nextPosition).alt(product.getTitle()).build());
        if (product.getImage() == null || product.getImage().isBlank()) {
            product.setImage(url);
            productRepository.save(product);
        }
        return productAdapter.mapToGetProductImageResponseDto(saved);
    }

    @Transactional
    @Caching(evict = { @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG,
            PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true) })
    public List<GetProductImageResponseDto> deleteGalleryImage(Long productId, Long imageId) {
        Product product = loadProduct(productId);
        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image " + imageId + " not found on product " + productId));
        String removedUrl = image.getUrl();
        imageRepository.delete(image);
        // The soft-delete UPDATE must land before reindexing, otherwise the row still holds
        // its old live_position and the replacement collides with the unique index.
        imageRepository.flush();
        List<ProductImage> remaining = imageRepository.findByProductIdOrderByPositionAsc(productId);
        reindex(remaining);
        syncThumbnail(product, remaining);
        // Only after the row is gone and the thumbnail has moved on; the storage adapter
        // ignores URLs it does not own (seeded picsum links, for example).
        deleteAfterCommit(removedUrl);
        return productAdapter.mapToGetProductImageResponseDtoList(remaining);
    }

    @Transactional
    @Caching(evict = { @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG,
            PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true) })
    public List<GetProductImageResponseDto> reorderGallery(Long productId, List<Long> imageIds) {
        Product product = loadProduct(productId);
        List<ProductImage> current = imageRepository.findByProductIdOrderByPositionAsc(productId);
        Set<Long> currentIds = current.stream().map(ProductImage::getId).collect(Collectors.toSet());
        if (imageIds == null || imageIds.size() != current.size()
                || !currentIds.equals(new HashSet<>(imageIds))) {
            throw new InvalidRequestException(
                    "imageIds must list every gallery image of product " + productId + " exactly once");
        }

        Map<Long, ProductImage> byId = current.stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));
        List<ProductImage> ordered = imageIds.stream().map(byId::get).collect(Collectors.toList());
        reindex(ordered);
        syncThumbnail(product, ordered);
        return productAdapter.mapToGetProductImageResponseDtoList(ordered);
    }

    /**
     * Removing the object inside the transaction would leave a surviving row pointing at a
     * missing file if the soft delete later rolls back, so wait for the commit.
     */
    private void deleteAfterCommit(String url) {
        if (url == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    imageStorage.delete(url);
                }
            });
        } else {
            imageStorage.delete(url);
        }
    }

    private void reindex(List<ProductImage> imagesInOrder) {
        for (int i = 0; i < imagesInOrder.size(); i++) {
            imagesInOrder.get(i).setPosition(-(i + 1));
        }
        imageRepository.saveAll(imagesInOrder);
        imageRepository.flush();
        for (int i = 0; i < imagesInOrder.size(); i++) {
            imagesInOrder.get(i).setPosition(i);
        }
        imageRepository.saveAll(imagesInOrder);
        imageRepository.flush();
    }

    private void syncThumbnail(Product product, List<ProductImage> imagesInOrder) {
        String first = imagesInOrder.isEmpty() ? null : imagesInOrder.get(0).getUrl();
        if (!Objects.equals(first, product.getImage())) {
            product.setImage(first);
            productRepository.save(product);
        }
    }

    private Product loadProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidRequestException("file is required");
        if (file.getSize() > MAX_BYTES) throw new InvalidRequestException("image must be under 5 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw new InvalidRequestException("only PNG, JPEG and WebP images are accepted");
        }
        try {
            return imageStorage.store(file.getBytes(), extension(contentType), contentType);
        } catch (IOException e) {
            throw new InvalidRequestException("failed to read uploaded image: " + e.getMessage());
        }
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
