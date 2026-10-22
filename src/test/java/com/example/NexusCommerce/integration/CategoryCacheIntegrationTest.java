package com.example.nexusCommerce.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.utils.PageResponse;

/**
 * Proves the declarative cache actually works end to end: the annotations only take
 * effect through the Spring proxy, so a unit test with mocked collaborators cannot
 * detect a missing {@code @EnableCaching} or a wrong cache name.
 */
@SpringBootTest(properties = "spring.cache.type=simple")
class CategoryCacheIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private CategoryRepository categoryRepository;

    // The category caches and the product caches are evicted together, so the product
    // repository is stubbed out to keep this class about cache behaviour, not schema state.
    @MockitoBean
    private ProductRepository productRepository;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    private Category category(Long id, String name) {
        Category category = Category.builder().name(name).build();
        category.setId(id);
        return category;
    }

    private Page<Category> page(List<Category> content) {
        return new PageImpl<>(content, PageRequest.of(0, 20), content.size());
    }

    @Test
    void repeatedReadsAreServedFromCache() {
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(page(List.of(category(1L, "Electronics"))));

        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());
        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());
        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());

        verify(categoryRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void differentPageKeysAreSeparateCacheEntries() {
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(page(List.of(category(1L, "Electronics"))));

        categoryService.getAllCategory(0, 10);
        categoryService.getAllCategory(0, 20);

        verify(categoryRepository, times(2)).findAll(any(Pageable.class));
    }

    @Test
    void createEvictsTheListCache() {
        Category created = category(2L, "Books");
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(page(List.of(category(1L, "Electronics"))))
                .thenReturn(page(List.of(category(1L, "Electronics"), created)));
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Books")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(created);

        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());

        categoryService.createCategory(CreateCategoryRequestDto.builder().name("Books").build());

        PageResponse<GetCategoryResponseDto> afterCreate = categoryService.getAllCategory(0, 20);
        assertEquals(2, afterCreate.getContent().size());
        verify(categoryRepository, times(2)).findAll(any(Pageable.class));
    }

    @Test
    void deleteEvictsTheListCache() {
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(page(List.of(category(1L, "Electronics"))))
                .thenReturn(page(List.of()));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Electronics")));
        when(productRepository.existsByCategory_Id(1L)).thenReturn(false);
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);

        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());

        categoryService.deleteCategory(1L);

        assertEquals(0, categoryService.getAllCategory(0, 20).getContent().size());
        verify(categoryRepository, times(2)).findAll(any(Pageable.class));
    }

    @Test
    void getByIdIsCachedPerKey() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Electronics")));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, "Books")));

        assertEquals("Electronics", categoryService.getCategoryById(1L).getName());
        assertEquals("Electronics", categoryService.getCategoryById(1L).getName());
        assertEquals("Books", categoryService.getCategoryById(2L).getName());

        verify(categoryRepository, times(1)).findById(1L);
        verify(categoryRepository, times(1)).findById(2L);
    }

    @Test
    void failedWriteDoesNotEvictAnything() {
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(page(List.of(category(1L, "Electronics"))));
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Electronics"))
                .thenReturn(Optional.of(category(1L, "Electronics")));

        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.createCategory(CreateCategoryRequestDto.builder().name("Electronics").build()));
        verify(categoryRepository, never()).save(any(Category.class));

        // The rejected write must not have invalidated the cached page.
        assertEquals(1, categoryService.getAllCategory(0, 20).getContent().size());
        verify(categoryRepository, times(1)).findAll(any(Pageable.class));
    }
}
