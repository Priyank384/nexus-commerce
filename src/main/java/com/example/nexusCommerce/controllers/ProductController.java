package com.example.nexusCommerce.controllers;

import java.util.List;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productServices;

    @GetMapping()
    @Operation(summary="Get All Poducts",
               description="Fetches all the products from the catalogue."
    )
    @Tag(name="Get APIs")
    public List<GetProductResponseDto> getAllProducts(){
        return productServices.getAllProducts();
    }

    @Operation(summary="Get Product using specific Id",
               description="Fetches the specific product with the given Id."
    )
    @GetMapping("/{id}")
    @Tag(name="Get APIs")
    public GetProductResponseDto getProductById(@PathVariable Long id){
        return productServices.getProductById(id);
    }

    @GetMapping("/{id}/details")
    public GetProductWithDetailsResponseDto getProductWithDetailsById(@PathVariable Long id){
        return productServices.getProductWithDetailsById(id);
    }

    @Operation(summary="Post a new Product",
               description="Add a new product in the catalogue."
    )
    @PostMapping
    @Tag(name="Post APIs")
    public Product createProduct(@RequestBody CreateProductRequestDto requestDto){
        return productServices.createProduct(requestDto);
    }

    @Operation(summary="Delete a Product with specific ID",
               description="Deletes the product from the catalogue with the specified Id."
    )
    @DeleteMapping("/{id}")
    @Tag(name="Delete APIs")
    public void deleteProduct(@PathVariable Long id){
        productServices.deleteProduct(id);
    }

    @Operation(summary="Get Products with Category Name",
               description="Fetches all the products from that specified category"
    )
    @GetMapping("/search")
    @Tag(name="Get APIs")
    public List<Product> findProductsByCategory(@RequestParam("categoryName") String category){
        return productServices.findByCategory(category);
    }

    // Task: Write an API to get all the unique categories

    @Operation(summary="Get all the unique Category available",
               description="Fetches the all the unique categories available in the catalogue."
    )
    @GetMapping("/uniqueCategories")
    @Tag(name="Get APIs")
    public List<String> getUniqueCategories(){
        return productServices.getUniqueCategories();
    }
}
