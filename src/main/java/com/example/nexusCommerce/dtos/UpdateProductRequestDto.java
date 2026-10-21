package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateProductRequestDto {

    // Every field is optional: updateProduct merges only what is present. A @NotBlank here
    // would reject a legitimate price-only or stock-only PUT, contradicting that partial-
    // update contract. Blank/whitespace titles are ignored by the service's own guard.
    private String title;

    private String description;

    @PositiveOrZero(message = "Product price must not be negative")
    private BigDecimal price;

    private String image;

    @DecimalMin(value = "0", message = "Product rating must be between 0 and 10")
    @DecimalMax(value = "10", message = "Product rating must be between 0 and 10")
    private BigDecimal rating;

    @PositiveOrZero(message = "Stock must not be negative")
    private Integer stock;

    private Long categoryId;
}
