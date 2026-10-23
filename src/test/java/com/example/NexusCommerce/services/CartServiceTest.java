package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.repositories.CartRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Cart;
import com.example.nexusCommerce.schema.CartItem;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private VariantResolver variantResolver;

    @InjectMocks
    private CartServiceImpl cartService;

    private Cart sampleCart() {
        User user = User.builder().name("Test User").email("test@example.com").build();
        user.setId(1L);
        Product product = Product.builder().title("Organic Cotton Crew Tee").build();
        product.setId(1L);
        ProductVariant variant = ProductVariant.builder().sku("PT-1-M-BLA").size("M").stock(10).build();
        variant.setId(11L);
        Cart cart = Cart.builder().user(user).build();
        cart.setId(3L);
        cart.getItems().add(CartItem.builder()
                .cart(cart)
                .product(product)
                .variant(variant)
                .quantity(3)
                .build());
        return cart;
    }

    @Test
    void checkout_requiresAShippingAddress() {
        Cart cart = sampleCart();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThrows(InvalidRequestException.class, () -> cartService.checkout(1L, null));
        assertThrows(InvalidRequestException.class, () -> cartService.checkout(1L, new CreateOrderRequestDto()));
        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_passesTheAddressAndVariantToTheOrder() {
        Cart cart = sampleCart();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderService.createOrder(any(CreateOrderRequestDto.class), eq(1L)))
                .thenReturn(GetOrderResponseDto.builder().id(9L).orderStatus(OrderStatus.PENDING).build());
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setAddressId(7L);

        cartService.checkout(1L, request);

        ArgumentCaptor<CreateOrderRequestDto> captor = ArgumentCaptor.forClass(CreateOrderRequestDto.class);
        verify(orderService).createOrder(captor.capture(), eq(1L));
        CreateOrderRequestDto sent = captor.getValue();
        assertEquals(7L, sent.getAddressId());
        assertEquals(1, sent.getOrderItems().size());
        assertEquals(1L, sent.getOrderItems().get(0).getProductId());
        assertEquals(11L, sent.getOrderItems().get(0).getVariantId());
        assertEquals(3, sent.getOrderItems().get(0).getQuantity());
        assertTrue(cart.getItems().isEmpty(), "a placed order empties the cart");
    }
}
