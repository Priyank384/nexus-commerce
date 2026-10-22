package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Every field is optional; the service merges only what is present. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVariantRequestDto {

    @Size(max = 64, message = "SKU must be at most 64 characters")
    private String sku;

    @Size(max = 20, message = "Size must be at most 20 characters")
    private String size;

    @Size(max = 60, message = "Colour name must be at most 60 characters")
    private String colorName;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour hex must be a #RRGGBB value")
    private String colorHex;

    @DecimalMin(value = "0", message = "Price delta must not be negative")
    private BigDecimal priceDelta;

    private String imageUrl;

    @PositiveOrZero(message = "Stock must not be negative")
    private Integer stock;
}
