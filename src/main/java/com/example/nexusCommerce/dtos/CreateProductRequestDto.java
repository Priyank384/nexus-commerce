package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateProductRequestDto {

    @NotBlank(message = "Product title must not be blank")
    private String title;

    private String description;

    @NotNull(message = "Product price must not be null")
    @PositiveOrZero(message = "Product price must not be negative")
    private BigDecimal price;

    private String image;

    @NotNull(message = "Category id must not be null")
    private Long categoryId;

    @NotNull(message = "Product rating must not be null")
    @DecimalMin(value = "0", message = "Product rating must be between 0 and 10")
    @DecimalMax(value = "10", message = "Product rating must be between 0 and 10")
    private BigDecimal rating;

    @PositiveOrZero(message = "Stock must not be negative")
    @Builder.Default
    private Integer stock = 0;
}
