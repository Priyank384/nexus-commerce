package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.adapters.ReviewAdapter;
import com.example.nexusCommerce.dtos.CreateReviewRequestDto;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewAdapter reviewAdapter;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderProductsRepository orderProductsRepository;

    public List<GetReviewResponseDto> getAllReviews() {
        List<Review> reviews = reviewRepository.findAll();
        return reviewAdapter.mapToGetReviewResponseDtoList(reviews);
    }

    public GetReviewResponseDto getReviewById(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        return reviewAdapter.mapToGetReviewResponseDto(review);
    }

    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        reviewRepository.delete(review);
    }

    public List<GetReviewResponseDto> getReviewsByProductId(Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        return reviewAdapter.mapToGetReviewResponseDtoList(reviews);
    }

    public List<GetReviewResponseDto> getReviewsByOrderId(Long orderId) {
        List<Review> reviews = reviewRepository.findByOrderId(orderId);
        return reviewAdapter.mapToGetReviewResponseDtoList(reviews);
    }

    @Transactional
    public GetReviewResponseDto createReview(CreateReviewRequestDto requestDto) {
        validateCreateReviewRequest(requestDto);

        Order order = orderRepository.findById(requestDto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + requestDto.getOrderId()));
        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDto.getProductId()));

        boolean productInOrder = orderProductsRepository.findByOrderId(order.getId()).stream()
                .anyMatch(op -> op.getProduct().getId().equals(product.getId()));
        if (!productInOrder) {
            throw new InvalidRequestException(
                    "Product " + requestDto.getProductId() + " is not part of order " + requestDto.getOrderId());
        }

        if (reviewRepository.existsByOrder_IdAndProduct_Id(order.getId(), product.getId())) {
            throw new DuplicateResourceException("A review already exists for this order and product");
        }

        String comment = requestDto.getComment();
        if (comment != null) {
            comment = comment.trim();
            if (comment.isEmpty()) {
                comment = null;
            }
        }

        Review review = Review.builder()
                .order(order)
                .product(product)
                .rating(requestDto.getRating())
                .comment(comment)
                .build();

        Review saved = reviewRepository.save(review);
        return reviewAdapter.mapToGetReviewResponseDto(saved);
    }

    private void validateCreateReviewRequest(CreateReviewRequestDto requestDto) {
        if (requestDto == null) {
            throw new InvalidRequestException("Review payload is required");
        }
        if (requestDto.getOrderId() == null) {
            throw new InvalidRequestException("orderId is required");
        }
        if (requestDto.getProductId() == null) {
            throw new InvalidRequestException("productId is required");
        }
        if (requestDto.getRating() == null) {
            throw new InvalidRequestException("rating is required");
        }
        if (requestDto.getRating().signum() <= 0) {
            throw new InvalidRequestException("rating must be greater than 0");
        }
        if (requestDto.getRating().compareTo(BigDecimal.TEN) > 0) {
            throw new InvalidRequestException("rating must be at most 10");
        }
    }
}
