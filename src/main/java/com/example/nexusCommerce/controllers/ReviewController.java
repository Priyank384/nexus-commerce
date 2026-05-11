package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateReviewRequestDto;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.services.ReviewService;
import com.example.nexusCommerce.utils.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GetReviewResponseDto>>> getAllReviews() {
        List<GetReviewResponseDto> reviews = reviewService.getAllReviews();
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GetReviewResponseDto>> createReview(
            @RequestBody CreateReviewRequestDto requestDto) {
        GetReviewResponseDto review = reviewService.createReview(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(review, "Review created successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(null, "Review deleted successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetReviewResponseDto>> getReviewById(@PathVariable Long id) {
        GetReviewResponseDto review = reviewService.getReviewById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(review, "Review fetched successfully"));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<GetReviewResponseDto>>> getReviewsByProductId(
            @PathVariable Long productId) {
        List<GetReviewResponseDto> reviews = reviewService.getReviewsByProductId(productId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<GetReviewResponseDto>>> getReviewsByOrderId(
            @PathVariable Long orderId) {
        List<GetReviewResponseDto> reviews = reviewService.getReviewsByOrderId(orderId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }
}
