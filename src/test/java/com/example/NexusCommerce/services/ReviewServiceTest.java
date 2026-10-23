package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.utils.PageResponse;

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
    private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User sampleUser() {
        User user = User.builder().name("Reviewer").email("reviewer@example.com").build();
        user.setId(7L);
        return user;
    }

    private Order sampleOrder() {
        Order order = Order.builder()
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .user(sampleUser())
                .build();
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
    void getAllReviews_mapsPageOfReviews() {
        Review review = Review.builder().rating(BigDecimal.valueOf(4)).build();
        review.setId(1L);
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).rating(BigDecimal.valueOf(4)).build();

        when(reviewRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));
        when(reviewAdapter.mapToGetReviewResponseDto(review)).thenReturn(dto);

        PageResponse<GetReviewResponseDto> result = reviewService.getAllReviews(0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getReviewById_returnsMappedReview() {
        Review review = Review.builder().rating(BigDecimal.valueOf(4)).build();
        review.setId(1L);
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).rating(BigDecimal.valueOf(4)).build();

        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewAdapter.mapToGetReviewResponseDto(review)).thenReturn(dto);

        GetReviewResponseDto result = reviewService.getReviewById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getReviewById_whenNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getReviewById(1L));
    }

    @Test
    void deleteReview_deletesWhenReviewExists() {
        Product product = sampleProduct();
        Review review = Review.builder().rating(BigDecimal.valueOf(4)).product(product).build();
        review.setId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.countByProductId(1L)).thenReturn(2L);
        when(reviewRepository.averageRatingByProductId(1L)).thenReturn(3.5);

        reviewService.deleteReview(1L);

        verify(reviewRepository).delete(review);
        assertEquals(0, product.getRating().compareTo(BigDecimal.valueOf(3.5)));
        assertEquals(2, product.getReviewCount());
    }

    @Test
    void deleteReview_whenNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.deleteReview(1L));
    }

    @Test
    void getReviewsByProductId_mapsMatchingReviews() {
        Review review = Review.builder().rating(BigDecimal.valueOf(4)).build();
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).productId(1L).build();

        when(reviewRepository.findByProductId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));
        when(reviewAdapter.mapToGetReviewResponseDto(review)).thenReturn(dto);

        PageResponse<GetReviewResponseDto> result = reviewService.getReviewsByProductId(1L, 0, 20);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getReviewsByOrderId_mapsMatchingReviews() {
        Review review = Review.builder().rating(BigDecimal.valueOf(4)).build();
        GetReviewResponseDto dto = GetReviewResponseDto.builder().id(1L).orderId(3L).build();

        when(reviewRepository.findByOrderId(eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));
        when(reviewAdapter.mapToGetReviewResponseDto(review)).thenReturn(dto);

        PageResponse<GetReviewResponseDto> result = reviewService.getReviewsByOrderId(3L, 0, 20);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void createReview_savesAndReturnsReview() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(5))
                .comment("Excellent")
                .build();
        Review saved = Review.builder().order(order).product(product).rating(BigDecimal.valueOf(5)).comment("Excellent").build();
        saved.setId(1L);
        GetReviewResponseDto response = GetReviewResponseDto.builder()
                .id(1L)
                .orderId(3L)
                .productId(1L)
                .userId(7L)
                .rating(BigDecimal.valueOf(5))
                .comment("Excellent")
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sampleUser()));
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewAdapter.mapToGetReviewResponseDto(saved)).thenReturn(response);

        GetReviewResponseDto result = reviewService.createReview(dto, 7L);

        assertEquals(BigDecimal.valueOf(5), result.getRating());
        assertEquals("Excellent", result.getComment());
        assertEquals(7L, result.getUserId());
    }

    @Test
    void createReview_whenNotOrderOwner_throwsForbiddenException() {
        Order order = sampleOrder();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> reviewService.createReview(dto, 42L));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void createReview_whenCommentBlank_storesNullComment() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .comment("   ")
                .build();
        Review saved = Review.builder().order(order).product(product).rating(BigDecimal.valueOf(4)).comment(null).build();
        saved.setId(1L);
        GetReviewResponseDto response = GetReviewResponseDto.builder()
                .id(1L)
                .orderId(3L)
                .productId(1L)
                .userId(7L)
                .rating(BigDecimal.valueOf(4))
                .comment(null)
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sampleUser()));
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewAdapter.mapToGetReviewResponseDto(saved)).thenReturn(response);

        GetReviewResponseDto result = reviewService.createReview(dto, 7L);

        assertNull(result.getComment());
    }

    @Test
    void createReview_whenOrderNotFound_throwsResourceNotFoundException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(99L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenProductNotInOrder_throwsInvalidRequestException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenDuplicateReview_throwsDuplicateResourceException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenRatingMissing_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenRatingTooHigh_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(6))
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenRatingBelowOne_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(0.5))
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenRatingZeroOrNegative_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.ZERO)
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
    }

    @Test
    void createReview_whenNoAuthenticatedUser_throwsInvalidRequestException() {
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, null));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void createReview_whenUserNotFound_throwsResourceNotFoundException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(4))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.createReview(dto, 7L));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void createReview_whenOrderIsUnpaid_throwsInvalidRequestException() {
        Order order = sampleOrder();
        order.setPaymentStatus(PaymentStatus.CREATED);
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(5))
                .build();

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThrows(InvalidRequestException.class, () -> reviewService.createReview(dto, 7L));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void createReview_recomputesProductRatingAndCount() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(5))
                .comment("Excellent")
                .build();
        Review saved = Review.builder().order(order).product(product)
                .rating(BigDecimal.valueOf(5)).comment("Excellent").build();
        saved.setId(1L);

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sampleUser()));
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewRepository.countByProductId(1L)).thenReturn(3L);
        when(reviewRepository.averageRatingByProductId(1L)).thenReturn(4.333);
        when(reviewAdapter.mapToGetReviewResponseDto(saved))
                .thenReturn(GetReviewResponseDto.builder().id(1L).build());

        reviewService.createReview(dto, 7L);

        // 4.333 is rounded to the one decimal the catalogue column stores.
        assertEquals(0, product.getRating().compareTo(BigDecimal.valueOf(4.3)));
        assertEquals(3, product.getReviewCount());
        verify(productRepository).save(product);
    }

    @Test
    void createReview_keepsCuratedRatingWhenNoLiveReviewsExist() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateReviewRequestDto dto = CreateReviewRequestDto.builder()
                .orderId(3L)
                .productId(1L)
                .rating(BigDecimal.valueOf(5))
                .build();
        Review saved = Review.builder().order(order).product(product).rating(BigDecimal.valueOf(5)).build();
        saved.setId(1L);

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderProductsRepository.existsByOrderIdAndProduct_Id(3L, 1L)).thenReturn(true);
        when(reviewRepository.existsByOrder_IdAndProduct_Id(3L, 1L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sampleUser()));
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);
        when(reviewRepository.countByProductId(1L)).thenReturn(0L);
        when(reviewAdapter.mapToGetReviewResponseDto(saved))
                .thenReturn(GetReviewResponseDto.builder().id(1L).build());

        reviewService.createReview(dto, 7L);

        assertEquals(0, product.getRating().compareTo(BigDecimal.valueOf(4.5)));
        assertEquals(0, product.getReviewCount());
    }
}
