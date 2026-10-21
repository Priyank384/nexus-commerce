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

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.UpdateProductRequestDto;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Products", description = "Product catalogue and inventory")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    @Operation(summary="Get all products",
               description="Fetches one page of the catalogue. Defaults to the first 20 products."
    )
    public ResponseEntity<ApiResponse<PageResponse<GetProductResponseDto>>> getAllProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size){
        PageResponse<GetProductResponseDto> products = productService.getAllProducts(page, Pagination.clampSize(size));
        return ResponseEntity.ok(ApiResponse.success(products, "Products fetched successfully!"));
    }

    @Operation(summary="Get Product using specific Id",
               description="Fetches the specific product with the given Id."
    )
    @GetMapping("/{id}")    public ResponseEntity<ApiResponse<GetProductResponseDto>> getProductById(@PathVariable Long id){
        GetProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product fetched successfully!"));
    }


    @Operation(summary="Get Product with category",
                description="Fetched the Product with other details like its category using specific ID."
    )
    @GetMapping("/{id}/details")    public ResponseEntity<ApiResponse<GetProductWithDetailsResponseDto>> getProductWithDetailsById(@PathVariable Long id){
        GetProductWithDetailsResponseDto product = productService.getProductWithDetailsById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product details fetched successfully!"));
    }

    @Operation(summary="Post a new Product",
               description="Add a new product in the catalogue. Admin only."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<GetProductResponseDto>> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto){
        GetProductResponseDto product = productService.createProduct(requestDto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(product, "Product created successfully!"));
    }

    @Operation(summary="Update a Product with specific ID",
               description="Updates an existing product in the catalogue. Admin only."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetProductResponseDto>> updateProduct(@PathVariable Long id,
                                                                            @Valid @RequestBody UpdateProductRequestDto requestDto){
        GetProductResponseDto product = productService.updateProduct(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success(product, "Product updated successfully!"));
    }

    @Operation(summary="Delete a Product with specific ID",
               description="Deletes the product from the catalogue with the specified Id. Admin only."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id){
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted successfully!"));
    }

    @Operation(summary="Get Products with Category Name",
               description="Fetches all the products from that specified category"
    )
    @GetMapping("/search")    public ResponseEntity<ApiResponse<PageResponse<GetProductWithDetailsResponseDto>>> findProductsByCategory(
            @RequestParam("categoryName") String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size){
        PageResponse<GetProductWithDetailsResponseDto> products = productService.findByCategory(category, page, Pagination.clampSize(size));
        return ResponseEntity.ok(ApiResponse.success(products, "Products fetched by category successfully!"));
    }

    // Task: Write an API to get all the unique categories

    @Operation(summary="Get all the unique Category available",
               description="Fetches the all the unique categories available in the catalogue."
    )
    @GetMapping("/uniqueCategories")    public ResponseEntity<ApiResponse<List<String>>> getUniqueCategories(){
        List<String> categories = productService.getUniqueCategories();
        return ResponseEntity.ok(ApiResponse.success(categories, "Unique categories fetched successfully!"));
    }
}
