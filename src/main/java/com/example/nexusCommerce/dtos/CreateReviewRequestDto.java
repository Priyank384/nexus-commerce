package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateReviewRequestDto {

    private Long orderId;

    private Long productId;

    private BigDecimal rating;

    private String comment;
}
