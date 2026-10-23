package com.example.nexusCommerce.services;

import java.util.List;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.dtos.ProductSearchRequestDto;
import com.example.nexusCommerce.dtos.UpdateProductRequestDto;
import com.example.nexusCommerce.utils.PageResponse;

public interface ProductService {
    PageResponse<GetProductResponseDto> getAllProducts(int page, int size);
    GetProductResponseDto getProductById(Long id);
    GetProductWithDetailsResponseDto getProductWithDetailsById(Long id);
    GetProductWithDetailsResponseDto getProductWithDetailsForAdmin(Long id);
    GetProductWithDetailsResponseDto getProductBySlug(String slug);
    PageResponse<GetProductWithDetailsResponseDto> search(ProductSearchRequestDto query);
    List<String> getAvailableSizes();
    List<String> getAvailableColors();
    List<GetVariantResponseDto> getVariants(Long productId);
    GetProductResponseDto createProduct(CreateProductRequestDto requestDto);
    GetProductResponseDto updateProduct(Long id, UpdateProductRequestDto requestDto);
    GetProductResponseDto setStock(Long id, Integer stock);
    void deleteProduct(Long id);
    PageResponse<GetProductWithDetailsResponseDto> findByCategory(String category, int page, int size);
    List<String> getUniqueCategories();
}
