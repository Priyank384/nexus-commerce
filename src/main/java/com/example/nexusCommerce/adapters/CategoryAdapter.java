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
                .parentId(resolveParentId(category))
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
                .parentId(resolveParentId(category))
                .build();
    }

    /**
     * parent_id is an insertable=false, updatable=false view of the association, so it is only
     * backfilled when Hibernate loads the row. A category that was just saved from a request
     * still has the association set, and reading the identifier off that proxy does not trigger
     * a load.
     */
    private static Long resolveParentId(Category category) {
        if (category.getParentId() != null) {
            return category.getParentId();
        }
        return category.getParent() != null ? category.getParent().getId() : null;
    }
}
