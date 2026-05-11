package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetReviewResponseDto {

    private Long id;

    private Long productId;

    private Long orderId;

    private BigDecimal rating;

    private String comment;

    private LocalDateTime createdAt;
}
