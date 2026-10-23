package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.GetProductImageResponseDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductImageRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductImage;

@ExtendWith(MockitoExtension.class)
class ProductImageUploadServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Spy
    private ProductAdapter productAdapter = new ProductAdapter();

    @InjectMocks
    private ProductImageUploadService service;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void pointStorageAtTempDir() {
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private static Product product() {
        Product product = Product.builder().title("Pleated Trouser").image("/uploads/products/old.png").build();
        product.setId(42L);
        return product;
    }

    private static ProductImage image(Long id, String url, int position) {
        ProductImage image = ProductImage.builder().url(url).position(position).alt("alt").build();
        image.setId(id);
        return image;
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "look.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    void upload_rewritesProductImageAndCreatesPrimaryWhenGalleryEmpty() throws Exception {
        Product product = product();
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L)).thenReturn(List.of());
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        String url = service.upload(42L, png());

        assertTrue(url.startsWith("/uploads/products/"));
        assertTrue(url.endsWith(".png"));
        assertEquals(url, product.getImage());
        verify(productRepository).save(product);
        verify(imageRepository).save(any(ProductImage.class));
        try (var files = Files.list(uploadDir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void upload_updatesExistingPrimaryRowInsteadOfAppending() {
        Product product = product();
        ProductImage primary = image(1L, "/uploads/products/old.png", 0);
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L)).thenReturn(List.of(primary));
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        String url = service.upload(42L, png());

        assertEquals(url, primary.getUrl());
        assertEquals(0, primary.getPosition());
        verify(imageRepository).save(primary);
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        when(productRepository.findById(42L)).thenReturn(Optional.of(product()));
        MockMultipartFile gif = new MockMultipartFile("file", "x.gif", "image/gif", new byte[] {1});

        assertThrows(InvalidRequestException.class, () -> service.upload(42L, gif));
        verify(productRepository, never()).save(any());
    }

    @Test
    void appendGalleryImage_putsTheImageAfterTheHighestPosition() {
        Product product = product();
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findTopByProductIdOrderByPositionDesc(42L))
                .thenReturn(Optional.of(image(1L, "/a.png", 3)));
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        GetProductImageResponseDto dto = service.appendGalleryImage(42L, png());

        assertEquals(9L, dto.getId());
        assertEquals(4, dto.getPosition());
        assertEquals("/uploads/products/old.png", product.getImage(), "existing thumbnail is untouched");
        verify(productRepository, never()).save(any());
    }

    @Test
    void appendGalleryImage_seedsTheThumbnailWhenProductHasNone() {
        Product product = Product.builder().title("Pleated Trouser").build();
        product.setId(42L);
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findTopByProductIdOrderByPositionDesc(42L)).thenReturn(Optional.empty());
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        GetProductImageResponseDto dto = service.appendGalleryImage(42L, png());

        assertEquals(0, dto.getPosition());
        assertEquals(dto.getUrl(), product.getImage());
        verify(productRepository).save(product);
    }

    @Test
    void deleteGalleryImage_reindexesSurvivorsAndPromotesTheNewFirst() {
        Product product = product();
        ProductImage removed = image(1L, "/a.png", 0);
        ProductImage second = image(2L, "/b.png", 1);
        ProductImage third = image(3L, "/c.png", 2);
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findByIdAndProductId(1L, 42L)).thenReturn(Optional.of(removed));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L)).thenReturn(List.of(second, third));

        List<GetProductImageResponseDto> remaining = service.deleteGalleryImage(42L, 1L);

        verify(imageRepository).delete(removed);
        assertEquals(2, remaining.size());
        assertEquals(0, second.getPosition());
        assertEquals(1, third.getPosition());
        assertEquals("/b.png", product.getImage());
        verify(productRepository).save(product);
    }

    @Test
    void deleteGalleryImage_clearsTheThumbnailWhenTheGalleryEmpties() {
        Product product = product();
        ProductImage only = image(1L, "/a.png", 0);
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findByIdAndProductId(1L, 42L)).thenReturn(Optional.of(only));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L)).thenReturn(List.of());

        List<GetProductImageResponseDto> remaining = service.deleteGalleryImage(42L, 1L);

        assertTrue(remaining.isEmpty());
        assertNull(product.getImage());
        verify(productRepository).save(product);
    }

    @Test
    void deleteGalleryImage_rejectsAnImageFromAnotherProduct() {
        when(productRepository.findById(42L)).thenReturn(Optional.of(product()));
        when(imageRepository.findByIdAndProductId(7L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteGalleryImage(42L, 7L));
        verify(imageRepository, never()).delete(any(ProductImage.class));
    }

    @Test
    void reorderGallery_appliesTheRequestedOrderAndSyncsThumbnail() {
        Product product = product();
        ProductImage a = image(1L, "/a.png", 0);
        ProductImage b = image(2L, "/b.png", 1);
        ProductImage c = image(3L, "/c.png", 2);
        when(productRepository.findById(42L)).thenReturn(Optional.of(product));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L)).thenReturn(List.of(a, b, c));

        List<GetProductImageResponseDto> ordered = service.reorderGallery(42L, List.of(3L, 1L, 2L));

        assertEquals(List.of("/c.png", "/a.png", "/b.png"),
                ordered.stream().map(GetProductImageResponseDto::getUrl).toList());
        assertEquals(0, c.getPosition());
        assertEquals(1, a.getPosition());
        assertEquals(2, b.getPosition());
        assertEquals("/c.png", product.getImage());
        verify(productRepository).save(product);
    }

    @Test
    void reorderGallery_rejectsAListThatDoesNotMatchTheLiveSet() {
        when(productRepository.findById(42L)).thenReturn(Optional.of(product()));
        when(imageRepository.findByProductIdOrderByPositionAsc(42L))
                .thenReturn(List.of(image(1L, "/a.png", 0), image(2L, "/b.png", 1)));

        assertThrows(InvalidRequestException.class, () -> service.reorderGallery(42L, List.of(1L)));
        assertThrows(InvalidRequestException.class, () -> service.reorderGallery(42L, List.of(1L, 1L)));
        assertThrows(InvalidRequestException.class, () -> service.reorderGallery(42L, List.of(1L, 9L)));
        verify(imageRepository, never()).saveAll(any());
    }
}
