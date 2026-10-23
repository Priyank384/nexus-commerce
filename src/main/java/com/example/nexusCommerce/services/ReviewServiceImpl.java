package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_ORDER;
import static com.example.nexusCommerce.configs.CacheConfig.REVIEW_BY_PRODUCT;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.ReviewAdapter;
import com.example.nexusCommerce.dtos.CreateReviewRequestDto;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.PaymentStatus;
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
    @CacheEvict(cacheNames = { REVIEW_ALL, REVIEW_BY_ID, REVIEW_BY_PRODUCT, REVIEW_BY_ORDER,
            PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH },
            allEntries = true)
    @Override
    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        Product product = review.getProduct();
        reviewRepository.delete(review);
        recomputeProductRating(product);
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
    @CacheEvict(cacheNames = { REVIEW_ALL, REVIEW_BY_ID, REVIEW_BY_PRODUCT, REVIEW_BY_ORDER,
            PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH },
            allEntries = true)
    @Override
    public GetReviewResponseDto createReview(CreateReviewRequestDto requestDto, Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new InvalidRequestException("An authenticated user is required to write a review");
        }

        BigDecimal rating = requestDto.getRating();
        if (rating == null || rating.compareTo(BigDecimal.ONE) < 0
                || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new InvalidRequestException("rating must be between 1 and 5");
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
            throw new ForbiddenException("You can only review your own orders");
        }

        // An unpaid order is a cart with a number attached: nothing was bought, so the review
        // would not rest on a purchase.
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidRequestException("Only a paid order can be reviewed");
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
        recomputeProductRating(product);
        return reviewAdapter.mapToGetReviewResponseDto(saved);
    }

    /**
     * The rating the catalogue sorts and renders follows the live reviews once there is one.
     * With none, the curated seed stays: a style that has never been reviewed must not claim
     * a zero-star average. The count is written either way so the UI can tell the two apart.
     */
    private void recomputeProductRating(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        long reviewCount = reviewRepository.countByProductId(product.getId());
        if (reviewCount > 0) {
            Double average = reviewRepository.averageRatingByProductId(product.getId());
            if (average != null) {
                product.setRating(BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
            }
        }
        product.setReviewCount((int) reviewCount);
        productRepository.save(product);
    }
}
