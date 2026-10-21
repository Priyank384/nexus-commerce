package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateReviewRequestDto;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.utils.PageResponse;

public interface ReviewService {
    PageResponse<GetReviewResponseDto> getAllReviews(int page, int size);
    GetReviewResponseDto getReviewById(Long id);
    PageResponse<GetReviewResponseDto> getReviewsByProductId(Long productId, int page, int size);
    PageResponse<GetReviewResponseDto> getReviewsByOrderId(Long orderId, int page, int size);
    GetReviewResponseDto createReview(CreateReviewRequestDto requestDto, Long authenticatedUserId);
    void deleteReview(Long id);
}
