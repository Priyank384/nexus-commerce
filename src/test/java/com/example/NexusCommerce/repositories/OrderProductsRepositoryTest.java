package com.example.NexusCommerce.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(TestJpaConfig.class)
class OrderProductsRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private OrderProductsRepository orderProductsRepository;

    private Order order;
    private Product phone;
    private Product laptop;
    private OrderProducts phoneLineItem;
    private OrderProducts laptopLineItem;

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
        order = Order.builder().status(OrderStatus.PENDING).build();
        phoneLineItem = OrderProducts.builder().order(order).product(phone).quantity(2).build();
        laptopLineItem = OrderProducts.builder().order(order).product(laptop).quantity(1).build();

        testEntityManager.persistAndFlush(category);
        testEntityManager.persistAndFlush(phone);
        testEntityManager.persistAndFlush(laptop);
        testEntityManager.persistAndFlush(order);
        testEntityManager.persistAndFlush(phoneLineItem);
        testEntityManager.persistAndFlush(laptopLineItem);
        testEntityManager.clear();
    }

    @Test
    void findByOrderId_returnsAllLineItemsForOrder() {
        List<OrderProducts> result = orderProductsRepository.findByOrderId(order.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> item.getQuantity() == 2));
        assertTrue(result.stream().anyMatch(item -> item.getQuantity() == 1));
    }

    @Test
    void findByOrderWithProduct_fetchesProductDetails() {
        Order managedOrder = testEntityManager.getEntityManager().find(Order.class, order.getId());

        List<OrderProducts> result = orderProductsRepository.findByOrderWithProduct(managedOrder);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> "Phone".equals(item.getProduct().getTitle())));
        assertTrue(result.stream().anyMatch(item -> "Laptop".equals(item.getProduct().getTitle())));
    }
}
