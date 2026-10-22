package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVariantRequestDto {

    @NotBlank(message = "SKU must not be blank")
    @Size(max = 64, message = "SKU must be at most 64 characters")
    private String sku;

    @NotBlank(message = "Size must not be blank")
    @Size(max = 20, message = "Size must be at most 20 characters")
    private String size;

    @NotBlank(message = "Colour name must not be blank")
    @Size(max = 60, message = "Colour name must be at most 60 characters")
    private String colorName;

    @NotBlank(message = "Colour hex must not be blank")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour hex must be a #RRGGBB value")
    private String colorHex;

    @NotNull(message = "Price delta must not be null")
    @DecimalMin(value = "0", message = "Price delta must not be negative")
    @Builder.Default
    private BigDecimal priceDelta = BigDecimal.ZERO;

    private String imageUrl;

    @NotNull(message = "Stock must not be null")
    @PositiveOrZero(message = "Stock must not be negative")
    @Builder.Default
    private Integer stock = 0;
}
