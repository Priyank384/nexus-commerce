package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_ORDER;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_PRODUCT;

import java.math.BigDecimal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewAdapter reviewAdapter;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderProductsRepository orderProductsRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = REVIEW_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetReviewResponseDto> getAllReviews(int page, int size) {
        return PageResponse.of(reviewRepository.findAll(Pagination.of(page, size)),
                reviewAdapter::mapToGetReviewResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = REVIEW_BY_ID, key = "#id")
    @Override
    public GetReviewResponseDto getReviewById(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        return reviewAdapter.mapToGetReviewResponseDto(review);
    }

    @Transactional
    @CacheEvict(cacheNames = { REVIEW_ALL, REVIEW_BY_ID, REVIEW_BY_PRODUCT, REVIEW_BY_ORDER }, allEntries = true)
    @Override
    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        reviewRepository.delete(review);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = REVIEW_BY_PRODUCT, key = "#productId + ':' + #page + ':' + #size")
    @Override
    public PageResponse<GetReviewResponseDto> getReviewsByProductId(Long productId, int page, int size) {
        return PageResponse.of(reviewRepository.findByProductId(productId, Pagination.of(page, size)),
                reviewAdapter::mapToGetReviewResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = REVIEW_BY_ORDER, key = "#orderId + ':' + #page + ':' + #size")
    @Override
    public PageResponse<GetReviewResponseDto> getReviewsByOrderId(Long orderId, int page, int size) {
        return PageResponse.of(reviewRepository.findByOrderId(orderId, Pagination.of(page, size)),
                reviewAdapter::mapToGetReviewResponseDto);
    }

    @Transactional
    @CacheEvict(cacheNames = { REVIEW_ALL, REVIEW_BY_ID, REVIEW_BY_PRODUCT, REVIEW_BY_ORDER }, allEntries = true)
    @Override
    public GetReviewResponseDto createReview(CreateReviewRequestDto requestDto, Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new InvalidRequestException("An authenticated user is required to write a review");
        }

        BigDecimal rating = requestDto.getRating();
        if (rating == null || rating.signum() <= 0 || rating.compareTo(BigDecimal.TEN) > 0) {
            throw new InvalidRequestException("rating must be between 0 (exclusive) and 10 (inclusive)");
        }

        String comment = requestDto.getComment();
        if (comment != null) {
            comment = comment.trim();
            if (comment.isEmpty()) {
                comment = null;
            }
        }

        Order order = orderRepository.findById(requestDto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + requestDto.getOrderId()));

        // Identity is taken from the token, and a customer may only review their own order.
        if (!order.getUser().getId().equals(authenticatedUserId)) {
            throw new InvalidRequestException("You can only review your own orders");
        }

        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + requestDto.getProductId()));

        // Exists-check instead of loading every line item and its product just to
        // answer "is this product in this order".
        if (!orderProductsRepository.existsByOrderIdAndProduct_Id(order.getId(), product.getId())) {
            throw new InvalidRequestException(
                    "Product " + requestDto.getProductId() + " is not part of order " + requestDto.getOrderId());
        }

        if (reviewRepository.existsByOrder_IdAndProduct_Id(order.getId(), product.getId())) {
            throw new DuplicateResourceException("A review already exists for this order and product");
        }

        Review review = Review.builder()
                .user(userRepository.findById(authenticatedUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists")))
                .order(order)
                .product(product)
                .rating(rating)
                .comment(comment)
                .build();

        Review saved = reviewRepository.save(review);
        return reviewAdapter.mapToGetReviewResponseDto(saved);
    }
}
