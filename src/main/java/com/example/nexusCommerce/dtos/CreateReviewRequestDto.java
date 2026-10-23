package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateReviewRequestDto {

    @NotNull(message = "orderId is required")
    @Positive(message = "orderId must be positive")
    private Long orderId;

    @NotNull(message = "productId is required")
    @Positive(message = "productId must be positive")
    private Long productId;

    @NotNull(message = "rating is required")
    @DecimalMin(value = "1", message = "rating must be between 1 and 5")
    @DecimalMax(value = "5", message = "rating must be between 1 and 5")
    private BigDecimal rating;

    @Size(max = 2000, message = "comment must be at most 2000 characters")
    private String comment;
}
