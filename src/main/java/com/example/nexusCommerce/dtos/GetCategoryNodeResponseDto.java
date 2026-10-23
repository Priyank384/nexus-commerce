package com.example.nexusCommerce.dtos;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

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

    /**
     * Self-referential, so springdoc cannot type the items without recursing; the description
     * keeps the property visible in Swagger and the frontend types the recursion locally.
     */
    @Schema(description = "Direct children in the same shape; empty on a leaf.")
    @Builder.Default
    private List<GetCategoryNodeResponseDto> children = new ArrayList<>();
}
