package com.example.nexusCommerce.services;

import java.util.List;

import com.example.nexusCommerce.dtos.CreateVariantRequestDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.dtos.UpdateVariantRequestDto;

public interface VariantService {
    List<GetVariantResponseDto> listByProduct(Long productId);
    GetVariantResponseDto create(Long productId, CreateVariantRequestDto requestDto);
    GetVariantResponseDto update(Long variantId, UpdateVariantRequestDto requestDto);
    void delete(Long variantId);
}
