package com.example.NexusCommerce.repositories;

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

import com.example.NexusCommerce.configs.TestJpaConfig;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Review;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(TestJpaConfig.class)
class ReviewRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private ReviewRepository reviewRepository;

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
        firstOrder = Order.builder().status(OrderStatus.DELIVERED).build();
        secondOrder = Order.builder().status(OrderStatus.DELIVERED).build();

        testEntityManager.persistAndFlush(category);
        testEntityManager.persistAndFlush(phone);
        testEntityManager.persistAndFlush(laptop);
        testEntityManager.persistAndFlush(firstOrder);
        testEntityManager.persistAndFlush(secondOrder);

        testEntityManager.persistAndFlush(
                OrderProducts.builder().order(firstOrder).product(phone).quantity(1).build());
        testEntityManager.persistAndFlush(
                OrderProducts.builder().order(secondOrder).product(laptop).quantity(1).build());

        phoneReview = Review.builder()
                .order(firstOrder)
                .product(phone)
                .rating(BigDecimal.valueOf(8.5))
                .comment("Great phone")
                .build();
        testEntityManager.persistAndFlush(phoneReview);
        testEntityManager.clear();
    }

    @Test
    void findByProductId_returnsReviewsForProduct() {
        List<Review> result = reviewRepository.findByProductId(phone.getId());

        assertEquals(1, result.size());
        assertEquals("Great phone", result.get(0).getComment());
        assertEquals(phone.getId(), result.get(0).getProduct().getId());
    }

    @Test
    void findByOrderId_returnsReviewsForOrder() {
        List<Review> result = reviewRepository.findByOrderId(firstOrder.getId());

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
}
