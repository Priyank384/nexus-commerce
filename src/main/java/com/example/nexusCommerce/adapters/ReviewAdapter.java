package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.schema.Review;

@Component
public class ReviewAdapter {

    public GetReviewResponseDto mapToGetReviewResponseDto(Review review) {
        return GetReviewResponseDto.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .productId(review.getProduct().getId())
                .orderId(review.getOrder().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
