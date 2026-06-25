package com.example.nexusCommerce.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.services.cache.CategoryRedisCache;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryRedisCache categoryRedisCache;

    public Category createCategory(CreateCategoryRequestDto requestDto){
        String normalizedName = validateAndNormalizeCategoryName(requestDto);
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateResourceException("Category with name '" + normalizedName + "' already exists!");
        }
        Category newcategory = Category.builder()
                            .name(normalizedName)
                            .build();
        return categoryRepository.save(newcategory);
    }

    public List<Category> getAllCategory(){
        Optional<List<Category>> cached = categoryRedisCache.getAll();
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Category> categories = categoryRepository.findAll();
        categoryRedisCache.putAll(categories);
        return categories;
    }

    public Category getCategoryById(Long id){
        // Optional<Category> cached = categoryRedisCache.getById(id);
        // if (cached.isPresent()) {
        //     return cached.get();
        // }

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));
        // categoryRedisCache.putById(id, category);
        return category;
    }

    public void deleteCategory(Long id){
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category with id: " + id + " Not Found!");
        }
        categoryRepository.deleteById(id);
    }

    public Category updateCategory(Long id, CreateCategoryRequestDto requestDto) {
        String normalizedName = validateAndNormalizeCategoryName(requestDto);
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));

        categoryRepository.findByNameIgnoreCase(normalizedName)
                .filter(category -> !category.getId().equals(id))
                .ifPresent(category -> {
                    throw new DuplicateResourceException("Category with name '" + normalizedName + "' already exists!");
                });

        existingCategory.setName(normalizedName);
        return categoryRepository.save(existingCategory);
    }

    private String validateAndNormalizeCategoryName(CreateCategoryRequestDto requestDto) {
        if (requestDto == null || requestDto.getName() == null || requestDto.getName().isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }
        return requestDto.getName().trim();
    }
}
