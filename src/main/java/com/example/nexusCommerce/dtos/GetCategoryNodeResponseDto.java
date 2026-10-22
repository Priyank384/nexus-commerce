package com.example.nexusCommerce.dtos;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One node of the category tree. {@code children} is populated by the service, which owns
 * the traversal; the adapter maps a single node in isolation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetCategoryNodeResponseDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long parentId;

    @Builder.Default
    private List<GetCategoryNodeResponseDto> children = new ArrayList<>();
}
