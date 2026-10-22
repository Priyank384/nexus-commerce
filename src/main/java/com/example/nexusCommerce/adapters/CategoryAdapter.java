package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetCategoryNodeResponseDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.schema.Category;

@Component
public class CategoryAdapter {

    public GetCategoryResponseDto mapToGetCategoryResponseDto(Category category) {
        return GetCategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public List<GetCategoryResponseDto> mapToGetCategoryResponseDtoList(List<Category> categories) {
        return categories.stream().map(this::mapToGetCategoryResponseDto).collect(Collectors.toList());
    }

    /**
     * Maps one node without descending into {@code children}, which is a lazy collection.
     * The service owns the traversal and attaches the mapped children.
     */
    public GetCategoryNodeResponseDto mapToGetCategoryNodeResponseDto(Category category) {
        return GetCategoryNodeResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .build();
    }
}
