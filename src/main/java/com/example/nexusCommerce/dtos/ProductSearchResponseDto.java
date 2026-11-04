package com.example.nexusCommerce.dtos;

import java.util.List;

import com.example.nexusCommerce.utils.PageResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The catalogue search page, flat so every existing consumer keeps reading
 * {@code data.content} unchanged, plus the two fields that explain what happened to the words
 * the shopper typed.
 *
 * <p>{@code textQuery} is the phrase the query actually matched, after any word that named a
 * colour or a size left it, and {@code inferredFacets} says which word became which filter.
 * Together they are what lets the storefront show "searching tee, in Black" and hand the
 * shopper back a URL that removes one of them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponseDto {

    private List<GetProductWithDetailsResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private String textQuery;
    private List<InferredFacetDto> inferredFacets;

    public static ProductSearchResponseDto of(PageResponse<GetProductWithDetailsResponseDto> page,
                                              String textQuery,
                                              List<InferredFacetDto> inferredFacets) {
        return ProductSearchResponseDto.builder()
                .content(page.getContent())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .textQuery(textQuery)
                .inferredFacets(inferredFacets == null ? List.of() : List.copyOf(inferredFacets))
                .build();
    }
}
