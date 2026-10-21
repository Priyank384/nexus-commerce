package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.schema.Category;

@Component
public class CategoryAdapter {

    public GetCategoryResponseDto mapToGetCategoryResponseDto(Category category) {
        return GetCategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public List<GetCategoryResponseDto> mapToGetCategoryResponseDtoList(List<Category> categories) {
        return categories.stream().map(this::mapToGetCategoryResponseDto).collect(Collectors.toList());
    }
}
