package com.example.nexusCommerce.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

import com.example.nexusCommerce.configs.JpaAuditingConfig;
import com.example.nexusCommerce.schema.Cart;
import com.example.nexusCommerce.schema.CartItem;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;

/**
 * What happens to live shopper state when the catalogue underneath it is removed. A cart line
 * points at a product and a variant, so this decides whether a shopper's cart still reads after
 * an admin retires a style — or throws. The service retires the line, which is what the V22
 * decision already established: a cart is live state, not purchase history.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
class CartItemRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    private User user;
    private Cart cart;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        user = testEntityManager.persistAndFlush(User.builder()
                .name("Shopper").email("shopper@example.com").passwordHash("x").build());
        Category category = testEntityManager.persistAndFlush(Category.builder().name("Tops").build());
        product = Product.builder()
                .title("Crew Tee").description("Tee")
                .price(new BigDecimal("890.00").setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.5)).stock(10).category(category).build();
        testEntityManager.persistAndFlush(product);
        variant = ProductVariant.builder()
                .product(product).sku("TEE-M-BLACK").size("M").colorName("Black")
                .colorHex("#000000").stock(10).build();
        testEntityManager.persistAndFlush(variant);
        cart = testEntityManager.persistAndFlush(Cart.builder().user(user).build());
        testEntityManager.persistAndFlush(CartItem.builder()
                .cart(cart).product(product).variant(variant).quantity(2).build());
        testEntityManager.clear();
    }

    @Test
    void cartLineReadsItsProductAndVariantWhileBothAreLive() {
        CartItem line = onlyLine();

        assertEquals("Crew Tee", line.getProduct().getTitle());
        assertEquals("M", line.getVariant().getSize());
    }

    @Test
    void cartReadBreaksWhenTheProductIsGoneButTheLineWasLeftBehind() {
        productRepository.deleteById(product.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        // CartRepository.findByUserId fetch-joins items.product, so the missing row is discovered
        // while the cart is materialised — before any DTO mapper gets a chance to guard it. This
        // is the hazard that makes retiring the line the only correct answer.
        assertThrows(JpaObjectRetrievalFailureException.class, this::onlyLine);
    }

    @Test
    void retireByProduct_letsTheCartReadCleanlyAfterTheStyleIsDeleted() {
        productRepository.deleteById(product.getId());
        cartItemRepository.retireByProduct(product.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        Cart owned = cartRepository.findByUserId(user.getId()).orElseThrow();

        assertTrue(owned.getItems().isEmpty());
    }

    @Test
    void retireByVariant_keepsTheLinesForSizesThatAreStillSold() {
        ProductVariant large = testEntityManager.persistAndFlush(ProductVariant.builder()
                .product(product).sku("TEE-L-BLACK").size("L").colorName("Black")
                .colorHex("#000000").stock(4).build());
        testEntityManager.persistAndFlush(CartItem.builder()
                .cart(cart).product(product).variant(large).quantity(1).build());
        testEntityManager.flush();
        testEntityManager.clear();

        variantRepository.deleteById(variant.getId());
        cartItemRepository.retireByVariant(variant.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        List<CartItem> lines = cartRepository.findByUserId(user.getId()).orElseThrow().getItems();

        assertEquals(1, lines.size());
        assertEquals("L", lines.get(0).getVariant().getSize());
    }

    private CartItem onlyLine() {
        List<CartItem> lines = cartRepository.findByUserId(user.getId()).orElseThrow().getItems();
        return lines.stream().findFirst().orElseThrow();
    }
}
