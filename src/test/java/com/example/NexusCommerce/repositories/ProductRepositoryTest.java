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
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@Import(TestJpaConfig.class)
public class ProductRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private ProductRepository productRepository;

    private Category electronicsCategory;
    private Category booksCategory;
    private Product phone;
    private Product laptop;

    @BeforeEach
    void setUp() {
        electronicsCategory = Category.builder().name("Electronics").build();
        booksCategory = Category.builder().name("Books").build();

        phone = Product.builder()
                .title("Phone")
                .description("A Phone")
                .price(BigDecimal.valueOf(999.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.5))
                .category(electronicsCategory)
                .build();

        laptop = Product.builder()
                .title("Laptop")
                .description("A Laptop")
                .price(BigDecimal.valueOf(1499.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.8))
                .category(electronicsCategory)
                .build();

        testEntityManager.persistAndFlush(electronicsCategory);
        testEntityManager.persistAndFlush(booksCategory);
        testEntityManager.persistAndFlush(phone);
        testEntityManager.persistAndFlush(laptop);

        testEntityManager.clear();
    }

    @Test
    void findProductWithDetailsById_whenFound_returnsProductWithCategory() {
        List<Product> result = productRepository.findProductWithDetailsById(phone.getId());

        assertEquals(1, result.size());
        assertEquals("Phone", result.get(0).getTitle());
        assertEquals("Electronics", result.get(0).getCategory().getName());
    }

    @Test
    void findByCategoryNameIgnoreCase_whenCategoryMatches_returnsProducts() {
        List<Product> result = productRepository.findByCategory_NameIgnoreCase("electronics");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> "Phone".equals(p.getTitle())));
        assertTrue(result.stream().anyMatch(p -> "Laptop".equals(p.getTitle())));
    }

    @Test
    void findByCategoryNameIgnoreCase_whenCategoryDoesNotMatch_returnsEmptyList() {
        List<Product> result = productRepository.findByCategory_NameIgnoreCase("Books");

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByTitleIgnoreCase_whenTitleExists_returnsTrue() {
        assertTrue(productRepository.existsByTitleIgnoreCase("phone"));
    }

    @Test
    void existsByTitleIgnoreCase_whenTitleDoesNotExist_returnsFalse() {
        assertFalse(productRepository.existsByTitleIgnoreCase("Tablet"));
    }

    @Test
    void findUniqueCategory_returnsDistinctCategoryNames() {
        Product book = Product.builder()
                .title("Novel")
                .description("A Novel")
                .price(BigDecimal.valueOf(19.99).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.2))
                .category(booksCategory)
                .build();
        testEntityManager.persistAndFlush(book);
        testEntityManager.clear();

        List<String> result = productRepository.findUniqueCategory();

        assertEquals(2, result.size());
        assertTrue(result.contains("Electronics"));
        assertTrue(result.contains("Books"));
    }
}
