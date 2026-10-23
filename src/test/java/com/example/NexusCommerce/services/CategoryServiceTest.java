package com.example.nexusCommerce.services;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.nexusCommerce.adapters.CategoryAdapter;
import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.dtos.UpdateCategoryRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.utils.PageResponse;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private CategoryAdapter categoryAdapter = new CategoryAdapter();

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createCategory_savesAndReturnsCategory() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("Test Category").build();
        Category savedCategory = Category.builder().name("Test Category").build();
        savedCategory.setId(1L);

        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Test Category")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        GetCategoryResponseDto result = categoryService.createCategory(dto);

        assertEquals("Test Category", result.getName());
        assertEquals(1L, result.getId());
    }

    @Test
    void createCategory_trimsNameBeforeSave() {
        CreateCategoryRequestDto dto = CreateCategoryRequestDto.builder().name("  Electronics  ").build();
        Category savedCategory = Category.builder().name("Electronics").build();
        savedCategory.setId(1L);

        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Electronics")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        GetCategoryResponseDto result = categoryService.createCategory(dto);

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
        Category taken = Category.builder().name("Electronics").build();
        taken.setId(2L);
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Electronics")).thenReturn(Optional.of(taken));

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(dto));
    }

    @Test
    void getAllCategory_returnsPageOfRepositoryCategories() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        when(categoryRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category), PageRequest.of(0, 20), 1));

        PageResponse<GetCategoryResponseDto> result = categoryService.getAllCategory(0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("Electronics", result.getContent().get(0).getName());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getCategoryById_returnsCategory() {
        Category category = Category.builder().name("Test Category").build();
        category.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        GetCategoryResponseDto result = categoryService.getCategoryById(1L);

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
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(liveCategory()));
        when(productRepository.existsByCategory_Id(1L)).thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_whenCategoryHasProducts_throwsInvalidRequestAndKeepsIt() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(liveCategory()));
        when(productRepository.existsByCategory_Id(1L)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> categoryService.deleteCategory(1L));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_whenNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(1L));
    }

    private static Category liveCategory() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        return category;
    }

    @Test
    void updateCategory_updatesNameWhenValid() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder().name("Updated Name").build();
        Category existing = Category.builder().name("Old Name").build();
        existing.setId(1L);
        Category updated = Category.builder().name("Updated Name").build();
        updated.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Updated Name")).thenReturn(Optional.empty());
        when(categoryRepository.save(existing)).thenReturn(updated);

        GetCategoryResponseDto result = categoryService.updateCategory(1L, dto);

        assertEquals("Updated Name", result.getName());
    }

    @Test
    void updateCategory_whenNotFound_throwsResourceNotFoundException() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder().name("Updated Name").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(1L, dto));
    }

    @Test
    void updateCategory_whenDuplicateNameForAnotherCategory_throwsDuplicateResourceException() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder().name("Electronics").build();
        Category existing = Category.builder().name("Old Name").build();
        existing.setId(1L);
        Category other = Category.builder().name("Electronics").build();
        other.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Electronics")).thenReturn(Optional.of(other));

        assertThrows(DuplicateResourceException.class, () -> categoryService.updateCategory(1L, dto));
    }

    @Test
    void updateCategory_movesACategoryBackToTopLevel() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder()
                .name("Denim")
                .moveToTopLevel(true)
                .build();
        Category parent = Category.builder().name("Men").build();
        parent.setId(2L);
        Category existing = Category.builder().name("Denim").parent(parent).parentId(2L).build();
        existing.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Denim")).thenReturn(Optional.empty());
        when(categoryRepository.save(existing)).thenReturn(existing);

        GetCategoryResponseDto result = categoryService.updateCategory(1L, dto);

        assertNull(result.getParentId());
        assertNull(existing.getParent());
        assertNull(existing.getParentId());
    }

    @Test
    void updateCategory_rejectsParentIdTogetherWithMoveToTopLevel() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder()
                .name("Denim")
                .parentId(2L)
                .moveToTopLevel(true)
                .build();
        Category existing = Category.builder().name("Denim").build();
        existing.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidRequestException.class, () -> categoryService.updateCategory(1L, dto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_rejectsBeingItsOwnParent() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder()
                .name("Denim")
                .parentId(1L)
                .build();
        Category existing = Category.builder().name("Denim").build();
        existing.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidRequestException.class, () -> categoryService.updateCategory(1L, dto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_rejectsMovingUnderItsOwnDescendant() {
        UpdateCategoryRequestDto dto = UpdateCategoryRequestDto.builder()
                .name("Men")
                .parentId(2L)
                .build();
        Category existing = Category.builder().name("Men").build();
        existing.setId(1L);
        Category child = Category.builder().name("Denim").parent(existing).parentId(1L).build();
        child.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(child));
        when(categoryRepository.findByNameIgnoreCaseAndParentId("Men", 2L)).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> categoryService.updateCategory(1L, dto));
        verify(categoryRepository, never()).save(any(Category.class));
    }
}
