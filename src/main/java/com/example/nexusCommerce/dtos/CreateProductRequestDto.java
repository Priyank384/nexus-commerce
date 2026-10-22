package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "Product title must be at most 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Product price must not be null")
    @PositiveOrZero(message = "Product price must not be negative")
    private BigDecimal price;

    /** Higher list price shown struck-through. Null means "not on sale". */
    @PositiveOrZero(message = "Compare-at price must not be negative")
    private BigDecimal compareAtPrice;

    private String image;

    @NotNull(message = "Category id must not be null")
    @Positive(message = "Category id must be positive")
    private Long categoryId;

    @NotNull(message = "Product rating must not be null")
    @DecimalMin(value = "0", message = "Product rating must be between 0 and 10")
    @DecimalMax(value = "10", message = "Product rating must be between 0 and 10")
    private BigDecimal rating;

    @PositiveOrZero(message = "Stock must not be negative")
    @Builder.Default
    private Integer stock = 0;

    /** Optional: derived from the title by the service when absent. */
    @Size(max = 120, message = "Slug must be at most 120 characters")
    private String slug;

    @Size(max = 120, message = "Brand must be at most 120 characters")
    private String brand;

    @Size(max = 255, message = "Material must be at most 255 characters")
    private String material;

    @Size(max = 500, message = "Care instructions must be at most 500 characters")
    private String careInstructions;

    @Pattern(regexp = "MEN|WOMEN|UNISEX|KIDS", message = "Gender must be MEN, WOMEN, UNISEX or KIDS")
    private String gender;

    /** Null means active -- see the no-args-constructor note on the other optional fields. */
    private Boolean isActive;

    @Valid
    @Builder.Default
    private List<CreateVariantRequestDto> variants = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CreateProductImageRequestDto> images = new ArrayList<>();
}
