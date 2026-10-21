package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_UNIQUE_CATEGORIES;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.CategoryAdapter;
import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryAdapter categoryAdapter;

    // A category name appears in the product search cache and the unique-category
    // aggregate, so those have to be invalidated alongside the category caches.
    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, PRODUCT_BY_CATEGORY, PRODUCT_UNIQUE_CATEGORIES },
            allEntries = true)
    @Override
    public GetCategoryResponseDto createCategory(CreateCategoryRequestDto requestDto){
        String normalizedName = validateAndNormalizeCategoryName(requestDto);
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateResourceException("Category with name '" + normalizedName + "' already exists!");
        }
        Category newCategory = Category.builder()
                            .name(normalizedName)
                            .build();
        return categoryAdapter.mapToGetCategoryResponseDto(categoryRepository.save(newCategory));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CATEGORY_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetCategoryResponseDto> getAllCategory(int page, int size){
        return PageResponse.of(categoryRepository.findAll(Pagination.of(page, size)),
                categoryAdapter::mapToGetCategoryResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CATEGORY_BY_ID, key = "#id")
    @Override
    public GetCategoryResponseDto getCategoryById(Long id){
        return categoryRepository.findById(id)
                .map(categoryAdapter::mapToGetCategoryResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));
    }

    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, PRODUCT_BY_CATEGORY, PRODUCT_UNIQUE_CATEGORIES },
            allEntries = true)
    @Override
    public void deleteCategory(Long id){
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category with id: " + id + " Not Found!");
        }
        // Soft-deleting a category that still owns live products would orphan them: they'd
        // vanish from every category-scoped read while the products themselves stay active.
        if (productRepository.existsByCategory_Id(id)) {
            throw new InvalidRequestException(
                    "Cannot delete category with id: " + id + " because it still contains products");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, PRODUCT_BY_CATEGORY, PRODUCT_UNIQUE_CATEGORIES },
            allEntries = true)
    @Override
    public GetCategoryResponseDto updateCategory(Long id, CreateCategoryRequestDto requestDto) {
        String normalizedName = validateAndNormalizeCategoryName(requestDto);
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));

        categoryRepository.findByNameIgnoreCase(normalizedName)
                .filter(category -> !category.getId().equals(id))
                .ifPresent(category -> {
                    throw new DuplicateResourceException("Category with name '" + normalizedName + "' already exists!");
                });

        existingCategory.setName(normalizedName);
        return categoryAdapter.mapToGetCategoryResponseDto(categoryRepository.save(existingCategory));
    }

    private String validateAndNormalizeCategoryName(CreateCategoryRequestDto requestDto) {
        if (requestDto == null || requestDto.getName() == null || requestDto.getName().isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }
        return requestDto.getName().trim();
    }
}
