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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryNodeResponseDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category catalogue")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    
    private final CategoryService categoryService;

    @Operation(summary = "Get the category tree",
               description = "Top-level categories with their subcategories nested one level deep, in one "
                             + "response. This is what the storefront nav renders; the flat list endpoint "
                             + "stays for admin grids. Public; cached.")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<GetCategoryNodeResponseDto>>> getCategoryTree(){
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryTree(), "Category tree fetched successfully!"));
    }

    @Operation(summary = "Create a category",
               description = "Adds a new category to the catalogue. Requires ROLE_ADMIN.")
    @PostMapping
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> createCategory(@Valid @RequestBody CreateCategoryRequestDto requestDto){
        GetCategoryResponseDto res = categoryService.createCategory(requestDto); 
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res, "Category Created Successfully!"));
    }

    @Operation(summary = "List categories",
               description = "Paged list of categories. Public; cached.")
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

    @Operation(summary = "Get a category by id",
               description = "Single category lookup. Public; cached.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> getCategoryById(@PathVariable Long id){
        GetCategoryResponseDto category = categoryService.getCategoryById(id);
        return ResponseEntity
                .ok(ApiResponse.success(category, "Category fetched successfully!"));
    }

    @Operation(summary = "Delete a category",
               description = "Soft-deletes the category. Rejected if it still has live products. Requires ROLE_ADMIN.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id){
        categoryService.deleteCategory(id);
        return ResponseEntity
                .ok(ApiResponse.success(null, "Category deleted successfully!"));
    }

    @Operation(summary = "Update a category",
               description = "Renames an existing category. Requires ROLE_ADMIN.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetCategoryResponseDto>> updateCategory(@PathVariable Long id,
                                                                @Valid @RequestBody CreateCategoryRequestDto requestDto){
        GetCategoryResponseDto updatedCategory = categoryService.updateCategory(id, requestDto);
        return ResponseEntity
                .ok(ApiResponse.success(updatedCategory, "Category updated successfully!"));
    }
}
