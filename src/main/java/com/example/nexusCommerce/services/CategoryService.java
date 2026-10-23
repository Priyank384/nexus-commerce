package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryNodeResponseDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.dtos.UpdateCategoryRequestDto;
import com.example.nexusCommerce.utils.PageResponse;

import java.util.List;

public interface CategoryService {
    GetCategoryResponseDto createCategory(CreateCategoryRequestDto requestDto);
    PageResponse<GetCategoryResponseDto> getAllCategory(int page, int size);
    GetCategoryResponseDto getCategoryById(Long id);
    void deleteCategory(Long id);
    GetCategoryResponseDto updateCategory(Long id, UpdateCategoryRequestDto requestDto);

    /** Men / Women / Kids with their subcategories nested -- one query, no lazy loads. */
    List<GetCategoryNodeResponseDto> getCategoryTree();
}
