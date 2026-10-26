package com.example.nexusCommerce.integration;

import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.repositories.AddressRepository;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.services.VariantResolver;

/**
 * Checkout, order edits and deletions all move variant stock, and the storefront reads stock
 * from the slug (PDP) and search read models as well as the id/list ones. Cache names live in
 * annotation strings, so a missed name compiles and silently keeps an "Add to bag" button on a
 * sold-out size until the TTL expires — exactly the bug this class pins down.
 */
@SpringBootTest(properties = "spring.cache.type=simple")
class OrderStockCacheIntegrationTest {

    private static final List<String> PRODUCT_READ_MODELS = List.of(
            PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH);

    @Autowired
    private OrderService orderService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderProductsRepository orderProductsRepository;

    @MockitoBean
    private OrderAdapter orderAdapter;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AddressRepository addressRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private ProductVariantRepository variantRepository;

    @MockitoBean
    private VariantResolver variantResolver;

    @BeforeEach
    void seedProductCaches() {
        for (String name : PRODUCT_READ_MODELS) {
            Cache cache = cacheManager.getCache(name);
            assertNotNull(cache, name);
            cache.put("stale-key", "stock=1");
        }
    }

    @Test
    void checkoutEvictsEveryProductReadModel() {
        User user = User.builder().name("Asha Rao").email("asha@nexus.test").build();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        Product product = Product.builder()
                .title("Organic Cotton Crew Tee")
                .price(BigDecimal.valueOf(890))
                .build();
        product.setId(1L);
        ProductVariant variant = ProductVariant.builder()
                .sku("PT-1-M-BLA")
                .size("M")
                .colorName("Black")
                .colorHex("#000000")
                .priceDelta(BigDecimal.ZERO)
                .build();
        variant.setId(11L);
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(variantResolver.resolve(product, 11L)).thenReturn(variant);
        when(variantResolver.unitPrice(product, variant)).thenReturn(BigDecimal.valueOf(890));
        when(variantRepository.decrementStock(11L, 1)).thenReturn(1);
        when(orderProductsRepository.saveAll(anyList())).thenReturn(List.of());
        when(orderAdapter.mapToGetOrderResponseDto(any(), anyList())).thenReturn(null);

        orderService.createOrder(CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder()
                        .productId(1L)
                        .variantId(11L)
                        .quantity(1)
                        .build()))
                .build(), 7L);

        assertProductReadModelsEvicted();
    }

    @Test
    void orderEditsEvictEveryProductReadModel() {
        Order order = Order.builder().status(OrderStatus.PENDING).build();
        order.setId(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderAdapter.mapToGetOrderResponseDto(any(), anyList())).thenReturn(null);

        orderService.updateOrder(1L, UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build(), 1L, true);

        assertProductReadModelsEvicted();
    }

    @Test
    void deletingAnOrderEvictsEveryProductReadModel() {
        Order order = Order.builder().status(OrderStatus.PENDING).build();
        order.setId(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderProductsRepository.findStockDeltasByOrderId(1L)).thenReturn(List.of());

        orderService.deleteOrder(1L);

        assertProductReadModelsEvicted();
    }

    private void assertProductReadModelsEvicted() {
        for (String name : PRODUCT_READ_MODELS) {
            assertNull(cacheManager.getCache(name).get("stale-key"),
                    name + " still serves pre-checkout stock");
        }
    }
}
