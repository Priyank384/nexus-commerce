package com.example.nexusCommerce.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.schema.Category;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category createCategory(CreateCategoryRequestDto requestDto){
        Category newcategory = Category.builder()
                            .name(requestDto.getName())
                            .build();
        return categoryRepository.save(newcategory);
    }

    public List<Category> getAllCategory(){
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id){
        return categoryRepository.findById(id)
                                 .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));
    }

    public void deleteCategory(Long id){
        categoryRepository.deleteById(id);
    }
}
