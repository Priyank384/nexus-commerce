package com.example.NexusCommerce.services;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.services.cache.CategoryRedisCache;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryRedisCache categoryRedisCache;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategory_savesAndReturnsCategory() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Test Category").build();
        Category savedCategory = Category.builder().name("Test Category").build();
        savedCategory.setId(1L);

        when(categoryRepository.existsByNameIgnoreCase("Test Category")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        Category result = categoryService.createCategory(dto);

        assertEquals("Test Category", result.getName());
        assertEquals(1L, result.getId());
    }

    @Test
    void createCategory_trimsNameBeforeSave() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("  Electronics  ").build();
        Category savedCategory = Category.builder().name("Electronics").build();
        savedCategory.setId(1L);

        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        Category result = categoryService.createCategory(dto);

        assertEquals("Electronics", result.getName());
    }

    @Test
    void createCategory_whenNameIsBlank_throwsInvalidRequestException() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("   ").build();

        assertThrows(InvalidRequestException.class, () -> categoryService.createCategory(dto));
    }

    @Test
    void createCategory_whenDuplicateName_throwsDuplicateResourceException() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Electronics").build();
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(dto));
    }

    @Test
    void getAllCategory_whenCacheHit_returnsCachedCategories() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        when(categoryRedisCache.getAll()).thenReturn(Optional.of(List.of(category)));

        List<Category> result = categoryService.getAllCategory();

        assertEquals(1, result.size());
        assertEquals("Electronics", result.get(0).getName());
        verify(categoryRepository, never()).findAll();
    }

    @Test
    void getAllCategory_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        when(categoryRedisCache.getAll()).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<Category> result = categoryService.getAllCategory();

        assertEquals(1, result.size());
        verify(categoryRedisCache).putAll(List.of(category));
    }

    @Test
    void getCategoryById_returnsCategory() {
        Category category = Category.builder().name("Test Category").build();
        category.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryById(1L);

        assertEquals("Test Category", result.getName());
        assertEquals(1L, result.getId());
    }

    @Test
    void getCategoryById_whenNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(1L));
    }

    @Test
    void deleteCategory_deletesWhenCategoryExists() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_whenNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(1L));
    }

    @Test
    void updateCategory_updatesNameWhenValid() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Updated Name").build();
        Category existing = Category.builder().name("Old Name").build();
        existing.setId(1L);
        Category updated = Category.builder().name("Updated Name").build();
        updated.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCase("Updated Name")).thenReturn(Optional.empty());
        when(categoryRepository.save(existing)).thenReturn(updated);

        Category result = categoryService.updateCategory(1L, dto);

        assertEquals("Updated Name", result.getName());
    }

    @Test
    void updateCategory_whenNotFound_throwsResourceNotFoundException() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Updated Name").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(1L, dto));
    }

    @Test
    void updateCategory_whenDuplicateNameForAnotherCategory_throwsDuplicateResourceException() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Electronics").build();
        Category existing = Category.builder().name("Old Name").build();
        existing.setId(1L);
        Category other = Category.builder().name("Electronics").build();
        other.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCase("Electronics")).thenReturn(Optional.of(other));

        assertThrows(DuplicateResourceException.class, () -> categoryService.updateCategory(1L, dto));
    }
}
