package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.utils.ApiResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    
    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> createCategory(@RequestBody CreateCategoryRequestDto requestDto){
        Category res = categoryService.createCategory(requestDto); 
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Category Created Successfully!"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getAllCategory(){
        List<Category> categories = categoryService.getAllCategory();
        return ResponseEntity
                .ok(ApiResponse.success(categories, "Categories fetched successfully!"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Category>> getCategoryById(@PathVariable Long id){
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity
                .ok(ApiResponse.success(category, "Category fetched successfully!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id){
        categoryService.deleteCategory(id);
        return ResponseEntity
                .ok(ApiResponse.success(null, "Category deleted successfully!"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Category>> updateCategory(@PathVariable Long id,
                                                                @RequestBody CreateCategoryRequestDto requestDto){
        Category updatedCategory = categoryService.updateCategory(id, requestDto);
        return ResponseEntity
                .ok(ApiResponse.success(updatedCategory, "Category updated successfully!"));
    }
}
