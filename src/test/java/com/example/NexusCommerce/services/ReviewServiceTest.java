package com.example.NexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;
import com.example.nexusCommerce.services.ReviewService;
import com.example.nexusCommerce.services.cache.ReviewRedisCache;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewAdapter reviewAdapter;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderProductsRepository orderProductsRepository;

    @Mock
    private ReviewRedisCache reviewRedisCache;

    @InjectMocks
    private ReviewService reviewService;

    private Order sampleOrder() {
        Order order = Order.builder().status(OrderStatus.DELIVERED).build();
        order.setId(3L);
        return order;
    }

    private Product sampleProduct() {
        Product product = Product.builder()
                .title("iPhone")
                .price(BigDecimal.valueOf(80000))
                .rating(BigDecimal.valueOf(4.5))
                .build();
        product.setId(1L);
        return product;
    }

    @Test
    void getAllReviews_whenCacheHit_returnsCachedReviews() {
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).productId(1L).orderId(3L).build();
        when(reviewRedisCache.getAll()).thenReturn(Optional.of(List.of(dto)));

        List<GetReviewResponseDto> result = reviewService.getAllReviews();

        assertEquals(1, result.size());
        verify(reviewRepository, never()).findAll();
    }

    @Test
    void getAllReviews_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Review review = Review.builder().rating(BigDecimal.valueOf(8)).build();
        review.setId(1L);
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).rating(BigDecimal.valueOf(8)).build();

        when(reviewRedisCache.getAll()).thenReturn(Optional.empty());
        when(reviewRepository.findAll()).thenReturn(List.of(review));
        when(reviewAdapter.mapToGetReviewResponseDtoList(List.of(review))).thenReturn(List.of(dto));

        List<GetReviewResponseDto> result = reviewService.getAllReviews();

        assertEquals(1, result.size());
        verify(reviewRedisCache).putAll(List.of(dto));
    }

    @Test
    void getReviewById_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Review review = Review.builder().rating(BigDecimal.valueOf(8)).build();
        review.setId(1L);
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).rating(BigDecimal.valueOf(8)).build();

        when(reviewRedisCache.getById(1L)).thenReturn(Optional.empty());
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewAdapter.mapToGetReviewResponseDto(review)).thenReturn(dto);

        GetReviewResponseDto result = reviewService.getReviewById(1L);

        assertEquals(1L, result.getId());
        verify(reviewRedisCache).putById(1L, dto);
    }

    @Test
    void getReviewById_whenNotFound_throwsResourceNotFoundException() {
        when(reviewRedisCache.getById(1L)).thenReturn(Optional.empty());
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getReviewById(1L));
    }

    @Test
    void deleteReview_deletesWhenReviewExists() {
        Review review = Review.builder().rating(BigDecimal.valueOf(8)).build();
        review.setId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(1L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_whenNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.deleteReview(1L));
    }

    @Test
    void getReviewsByProductId_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Review review = Review.builder().rating(BigDecimal.valueOf(8)).build();
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).productId(1L).build();

        when(reviewRedisCache.getByProductId(1L)).thenReturn(Optional.empty());
        when(reviewRepository.findByProductId(1L)).thenReturn(List.of(review));
        when(reviewAdapter.mapToGetReviewResponseDtoList(List.of(review))).thenReturn(List.of(dto));

        List<GetReviewResponseDto> result = reviewService.getReviewsByProductId(1L);

        assertEquals(1, result.size());
        verify(reviewRedisCache).putByProductId(1L, List.of(dto));
    }

    @Test
    void getReviewsByOrderId_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Review review = Review.builder().rating(BigDecimal.valueOf(8)).build();
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).orderId(3L).build();

        when(reviewRedisCache.getByOrderId(3L)).thenReturn(Optional.empty());
        when(reviewRepository.findByOrderId(3L)).thenReturn(List.of(review));
        when(reviewAdapter.mapToGetReviewResponseDtoList(List.of(review))).thenReturn(List.of(dto));

        List<GetReviewResponseDto> result = reviewService.getReviewsByOrderId(3L);

        assertEquals(1, result.size());
        verify(reviewRedisCache).putByOrderId(3L, List.of(dto));
    }

    @Test
    void createReview_savesAndReturnsReview() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(9))
                .comment("Excellent")
                .build();
        Review saved = Review.builder().order(order).product(product).rating(BigDecimal.valueOf(9)).comment("Excellent").build();
        saved.setId(1L);
        GetReviewResponseDto response = GetReviewResponseDto.builder()
                .id(1L)
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(9))
                .comment("Excellent")
                .build();
        OrderProducts lineItem = OrderProducts.builder().order(order).product(product).quantity(1).build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.findByOrderId(3L)).thenReturn(List.of(lineItem));
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewAdapter.mapToGetReviewResponseDto(saved)).thenReturn(response);

        GetReviewResponseDto result = reviewService.createReview(dto);

        assertEquals(BigDecimal.valueOf(9), result.getRating());
        assertEquals("Excellent", result.getComment());
    }

    @Test
    void createReview_whenCommentBlank_storesNullComment() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(8))
                .comment("   ")
                .build();
        Review saved = Review.builder().order(order).product(product).rating(BigDecimal.valueOf(8)).comment(null).build();
        saved.setId(1L);
        GetReviewResponseDto response = GetReviewResponseDto.builder()
                .id(1L)
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(8))
                .comment(null)
                .build();
        OrderProducts lineItem = OrderProducts.builder().order(order).product(product).quantity(1).build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.findByOrderId(3L)).thenReturn(List.of(lineItem));
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewAdapter.mapToGetReviewResponseDto(saved)).thenReturn(response);

        GetReviewResponseDto result = reviewService.createReview(dto);

        assertNull(result.getComment());
    }

    @Test
    void createReview_whenOrderNotFound_throwsResourceNotFoundException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(99L)
                .productId(1L)
                .rating(BigDecimal.valueOf(8))
                .build();

        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.createReview(dto));
    }

    @Test
    void createReview_whenProductNotInOrder_throwsInvalidRequestException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(8))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.findByOrderId(3L)).thenReturn(List.of());

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto));
    }

    @Test
    void createReview_whenDuplicateReview_throwsDuplicateResourceException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(8))
                .build();
        OrderProducts lineItem = OrderProducts.builder().order(order).product(product).quantity(1).build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.findByOrderId(3L)).thenReturn(List.of(lineItem));
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> reviewService.createReview(dto));
    }

    @Test
    void createReview_whenRatingMissing_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto));
    }

    @Test
    void createReview_whenRatingTooHigh_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(11))
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto));
    }

    @Test
    void createReview_whenRatingZeroOrNegative_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.ZERO)
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto));
    }
}
