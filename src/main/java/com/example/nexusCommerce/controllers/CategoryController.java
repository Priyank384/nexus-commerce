package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;

@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category catalogue")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    
    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> createCategory(@Valid @RequestBody CreateCategoryRequestDto requestDto){
        GetCategoryResponseDto res = categoryService.createCategory(requestDto); 
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Category Created Successfully!"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GetCategoryResponseDto>>> getAllCategory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size){
        // Normalised here, at the web boundary, because the cache key is built from these
        // values inside the service: an unclamped size would mint a distinct key per
        // arbitrary number while the payload stays identical.
        PageResponse<GetCategoryResponseDto> categories = categoryService.getAllCategory(page, Pagination.clampSize(size));
        return ResponseEntity
                .ok(ApiResponse.success(categories, "Categories fetched successfully!"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> getCategoryById(@PathVariable Long id){
        GetCategoryResponseDto category = categoryService.getCategoryById(id);
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
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> updateCategory(@PathVariable Long id,
                                                                @Valid @RequestBody CreateCategoryRequestDto requestDto){
        GetCategoryResponseDto updatedCategory = categoryService.updateCategory(id, requestDto);
        return ResponseEntity
                .ok(ApiResponse.success(updatedCategory, "Category updated successfully!"));
    }
}
