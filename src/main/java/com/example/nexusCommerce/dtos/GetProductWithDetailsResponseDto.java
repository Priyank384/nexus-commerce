package com.example.nexusCommerce.dtos;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GetProductWithDetailsResponseDto extends GetProductResponseDto {
    private String categorySlug;

    private String material;
    private String careInstructions;
    private String gender;

    /**
     * Empty for list/search responses: variants and gallery images are only loaded for
     * single-product reads, because fetching them per row would be an N+1.
     */
    @Builder.Default
    private List<GetVariantResponseDto> variants = new ArrayList<>();

    @Builder.Default
    private List<GetProductImageResponseDto> images = new ArrayList<>();
}
