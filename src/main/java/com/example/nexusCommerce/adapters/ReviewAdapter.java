package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.schema.BaseEntity;
import com.example.nexusCommerce.schema.Review;

@Component
public class ReviewAdapter {

    public GetReviewResponseDto mapToGetReviewResponseDto(Review review) {
        return GetReviewResponseDto.builder()
                .id(review.getId())
                .userId(orMirror(review.getUserId(), review.getUser()))
                .productId(orMirror(review.getProductId(), review.getProduct()))
                .orderId(orMirror(review.getOrderId(), review.getOrder()))
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    /**
     * The read-only FK column is what a loaded review carries, but it is null on an entity that
     * was just saved in the same transaction, so fall back to the association there.
     */
    private static Long orMirror(Long mirror, BaseEntity association) {
        return mirror != null ? mirror : (association != null ? association.getId() : null);
    }
}
