package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.utils.PageResponse;

public interface CategoryService {
    GetCategoryResponseDto createCategory(CreateCategoryRequestDto requestDto);
    PageResponse<GetCategoryResponseDto> getAllCategory(int page, int size);
    GetCategoryResponseDto getCategoryById(Long id);
    void deleteCategory(Long id);
    GetCategoryResponseDto updateCategory(Long id, CreateCategoryRequestDto requestDto);
}
