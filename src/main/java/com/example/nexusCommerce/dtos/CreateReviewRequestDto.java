package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @DecimalMin(value = "0.1", message = "rating must be greater than 0")
    @DecimalMax(value = "10", message = "rating must be at most 10")
    private BigDecimal rating;

    private String comment;
}
