package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
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
public class UpdateProductRequestDto {

    // Every field is optional: updateProduct merges only what is present. A @NotBlank here
    // would reject a legitimate price-only or stock-only PUT, contradicting that partial-
    // update contract. Blank/whitespace titles are ignored by the service's own guard.
    private String title;

    private String description;

    @PositiveOrZero(message = "Product price must not be negative")
    private BigDecimal price;

    @PositiveOrZero(message = "Compare-at price must not be negative")
    private BigDecimal compareAtPrice;

    private String image;

    @DecimalMin(value = "0", message = "Product rating must be between 0 and 10")
    @DecimalMax(value = "10", message = "Product rating must be between 0 and 10")
    private BigDecimal rating;

    @PositiveOrZero(message = "Stock must not be negative")
    private Integer stock;

    private Long categoryId;

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

    private Boolean isActive;

    // Deliberately no variants/images here: order lines reference variant ids, so replacing
    // a whole variant set through a product PUT would silently orphan purchases. Variants
    // and gallery images are managed through their own endpoints instead.
}
