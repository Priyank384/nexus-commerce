package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productServices;

    @GetMapping
    @Operation(summary="Get All Poducts",
               description="Fetches all the products from the catalogue."
    )
    @Tag(name="Get APIs")
    public ResponseEntity<ApiResponse<List<GetProductResponseDto>>> getAllProducts(){
        List<GetProductResponseDto> products = productServices.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success(products, "Products fetched successfully!"));
    }

    @Operation(summary="Get Product using specific Id",
               description="Fetches the specific product with the given Id."
    )
    @GetMapping("/{id}")
    @Tag(name="Get APIs")
    public ResponseEntity<ApiResponse<GetProductResponseDto>> getProductById(@PathVariable Long id){
        GetProductResponseDto product = productServices.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product fetched successfully!"));
    }


    @Operation(summary="Get Product with category",
                description="Fetched the Product with other details like its category using specific ID."
    )
    @GetMapping("/{id}/details")
    @Tag(name="Get APIs")
    public ResponseEntity<ApiResponse<GetProductWithDetailsResponseDto>> getProductWithDetailsById(@PathVariable Long id){
        GetProductWithDetailsResponseDto product = productServices.getProductWithDetailsById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product details fetched successfully!"));
    }

    @Operation(summary="Post a new Product",
               description="Add a new product in the catalogue."
    )
    @PostMapping
    @Tag(name="Post APIs")
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody CreateProductRequestDto requestDto){
        Product product = productServices.createProduct(requestDto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(product, "Product created successfully!"));
    }

    @Operation(summary="Delete a Product with specific ID",
               description="Deletes the product from the catalogue with the specified Id."
    )
    @DeleteMapping("/{id}")
    @Tag(name="Delete APIs")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id){
        productServices.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted successfully!"));
    }

    @Operation(summary="Get Products with Category Name",
               description="Fetches all the products from that specified category"
    )
    @GetMapping("/search")
    @Tag(name="Get APIs")
    public ResponseEntity<ApiResponse<List<Product>>> findProductsByCategory(@RequestParam("categoryName") String category){
        List<Product> products = productServices.findByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(products, "Products fetched by category successfully!"));
    }

    // Task: Write an API to get all the unique categories

    @Operation(summary="Get all the unique Category available",
               description="Fetches the all the unique categories available in the catalogue."
    )
    @GetMapping("/uniqueCategories")
    @Tag(name="Get APIs")
    public ResponseEntity<ApiResponse<List<String>>> getUniqueCategories(){
        List<String> categories = productServices.getUniqueCategories();
        return ResponseEntity.ok(ApiResponse.success(categories, "Unique categories fetched successfully!"));
    }
}
