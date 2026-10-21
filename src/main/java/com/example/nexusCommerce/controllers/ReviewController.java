package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateReviewRequestDto;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.ReviewService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Reviews", description = "Product reviews and moderation")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GetReviewResponseDto>>> getAllReviews(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetReviewResponseDto> reviews = reviewService.getAllReviews(page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GetReviewResponseDto>> createReview(
            @Valid @RequestBody CreateReviewRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        GetReviewResponseDto review = reviewService.createReview(requestDto, caller.id());
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
    public ResponseEntity<ApiResponse<PageResponse<GetReviewResponseDto>>> getReviewsByProductId(
            @PathVariable Long productId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetReviewResponseDto> reviews = reviewService.getReviewsByProductId(productId, page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PageResponse<GetReviewResponseDto>>> getReviewsByOrderId(
            @PathVariable Long orderId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetReviewResponseDto> reviews = reviewService.getReviewsByOrderId(orderId, page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(reviews, "Reviews fetched successfully"));
    }
}
