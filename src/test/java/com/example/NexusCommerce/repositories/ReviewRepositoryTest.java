package com.example.nexusCommerce.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

import com.example.nexusCommerce.adapters.ReviewAdapter;
import com.example.nexusCommerce.configs.JpaAuditingConfig;
import com.example.nexusCommerce.dtos.GetReviewResponseDto;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;
import com.example.nexusCommerce.schema.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
class ReviewRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    private Order firstOrder;
    private Order secondOrder;
    private Product phone;
    private Product laptop;
    private Review phoneReview;

    @BeforeEach
    void setUp() {
        Category category = Category.builder().name("Electronics").build();
        phone = Product.builder()
                .title("Phone")
                .description("A Phone")
                .price(BigDecimal.valueOf(999.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.5))
                .category(category)
                .build();
        laptop = Product.builder()
                .title("Laptop")
                .description("A Laptop")
                .price(BigDecimal.valueOf(1499.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.8))
                .category(category)
                .build();
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        firstOrder = Order.builder().status(OrderStatus.DELIVERED).user(user).build();
        secondOrder = Order.builder().status(OrderStatus.DELIVERED).user(user).build();

        testEntityManager.persistAndFlush(category);
        testEntityManager.persistAndFlush(user);
        testEntityManager.persistAndFlush(phone);
        testEntityManager.persistAndFlush(laptop);
        testEntityManager.persistAndFlush(firstOrder);
        testEntityManager.persistAndFlush(secondOrder);

        testEntityManager.persistAndFlush(
                OrderProducts.builder().order(firstOrder).product(phone).quantity(1).unitPrice(phone.getPrice()).productName(phone.getTitle()).build());
        testEntityManager.persistAndFlush(
                OrderProducts.builder().order(secondOrder).product(laptop).quantity(1).unitPrice(laptop.getPrice()).productName(laptop.getTitle()).build());

        phoneReview = Review.builder()
                .user(user)
                .order(firstOrder)
                .product(phone)
                .rating(BigDecimal.valueOf(4.5))
                .comment("Great phone")
                .build();
        testEntityManager.persistAndFlush(phoneReview);
        testEntityManager.clear();
    }

    @Test
    void findByProductId_returnsReviewsForProduct() {
        List<Review> result = reviewRepository.findByProductId(phone.getId(), Pageable.unpaged()).getContent();

        assertEquals(1, result.size());
        assertEquals("Great phone", result.get(0).getComment());
        assertEquals(phone.getId(), result.get(0).getProduct().getId());
    }

    @Test
    void findByOrderId_returnsReviewsForOrder() {
        List<Review> result = reviewRepository.findByOrderId(firstOrder.getId(), Pageable.unpaged()).getContent();

        assertEquals(1, result.size());
        assertEquals(firstOrder.getId(), result.get(0).getOrder().getId());
    }

    @Test
    void existsByOrderIdAndProductId_whenReviewExists_returnsTrue() {
        assertTrue(reviewRepository.existsByOrder_IdAndProduct_Id(firstOrder.getId(), phone.getId()));
    }

    @Test
    void existsByOrderIdAndProductId_whenReviewDoesNotExist_returnsFalse() {
        assertFalse(reviewRepository.existsByOrder_IdAndProduct_Id(secondOrder.getId(), phone.getId()));
    }

    @Test
    void averageAndCountIgnoreSoftDeletedReviews() {
        // setUp cleared the persistence context, so the reviewer is re-read as a managed row
        // rather than reusing a detached association.
        User reviewer = reviewRepository.findById(phoneReview.getId()).orElseThrow().getUser();
        Review second = Review.builder()
                .user(reviewer)
                .order(secondOrder)
                .product(phone)
                .rating(BigDecimal.valueOf(3.5))
                .build();
        testEntityManager.persistAndFlush(second);

        assertEquals(2, reviewRepository.countByProductId(phone.getId()));
        assertEquals(4.0, reviewRepository.averageRatingByProductId(phone.getId()), 0.001);

        reviewRepository.delete(second);
        testEntityManager.flush();
        testEntityManager.clear();

        assertEquals(1, reviewRepository.countByProductId(phone.getId()));
        assertEquals(4.5, reviewRepository.averageRatingByProductId(phone.getId()), 0.001);
    }

    @Test
    void reviewStaysReadableWhenTheReviewedProductIsRetired() {
        productRepository.deleteById(phone.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        List<Review> surviving = reviewRepository
                .findByProductId(phone.getId(), Pageable.unpaged()).getContent();

        assertEquals(1, surviving.size());
        // A review is purchase history: the soft-deleted product still satisfies the FK, so the
        // row survives. What would break is mapping it — the association is gone but the read-only
        // FK mirror is not, and that is what the response DTO is built from.
        GetReviewResponseDto dto = new ReviewAdapter().mapToGetReviewResponseDto(surviving.get(0));

        assertEquals(phone.getId(), dto.getProductId());
        assertEquals(firstOrder.getId(), dto.getOrderId());
        assertEquals("Great phone", dto.getComment());
    }
}
