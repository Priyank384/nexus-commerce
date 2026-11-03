package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.ShippingAddressRequestDto;
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
import com.example.nexusCommerce.security.ShopperIdentity;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    /** The shape a real minted session has; the service never re-validates it, only the header does. */
    private static final String GUEST_TOKEN = "a".repeat(43);

    private static final ShopperIdentity MEMBER = new ShopperIdentity(1L, null);
    private static final ShopperIdentity GUEST = new ShopperIdentity(null, GUEST_TOKEN);

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

    /** Every read model prices its lines through the resolver; these tests are about counts. */
    @BeforeEach
    void priceEveryLine() {
        lenient().when(variantResolver.unitPrice(any(), any())).thenReturn(BigDecimal.valueOf(890));
    }

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

    private Cart guestCartWith(int variantId, int quantity, int stock) {
        Product product = Product.builder().title("Organic Cotton Crew Tee").build();
        product.setId(1L);
        ProductVariant variant = ProductVariant.builder().sku("PT-1-M-BLA").size("M").stock(stock).build();
        variant.setId((long) variantId);
        Cart cart = Cart.builder().guestToken(GUEST_TOKEN).build();
        cart.setId(9L);
        cart.getItems().add(CartItem.builder()
                .cart(cart)
                .product(product)
                .variant(variant)
                .quantity(quantity)
                .build());
        return cart;
    }

    @Test
    void checkout_requiresAShippingAddress() {
        Cart cart = sampleCart();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThrows(InvalidRequestException.class, () -> cartService.checkout(MEMBER, null));
        assertThrows(InvalidRequestException.class, () -> cartService.checkout(MEMBER, new CreateOrderRequestDto()));
        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_passesTheAddressAndVariantToTheOrder() {
        Cart cart = sampleCart();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderService.createOrder(any(CreateOrderRequestDto.class), eq(MEMBER)))
                .thenReturn(GetOrderResponseDto.builder().id(9L).orderStatus(OrderStatus.PENDING).build());
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setAddressId(7L);

        cartService.checkout(MEMBER, request);

        ArgumentCaptor<CreateOrderRequestDto> captor = ArgumentCaptor.forClass(CreateOrderRequestDto.class);
        verify(orderService).createOrder(captor.capture(), eq(MEMBER));
        CreateOrderRequestDto sent = captor.getValue();
        assertEquals(7L, sent.getAddressId());
        assertEquals(1, sent.getOrderItems().size());
        assertEquals(1L, sent.getOrderItems().get(0).getProductId());
        assertEquals(11L, sent.getOrderItems().get(0).getVariantId());
        assertEquals(3, sent.getOrderItems().get(0).getQuantity());
        assertTrue(cart.getItems().isEmpty(), "a placed order empties the cart");
    }

    @Test
    void checkout_asGuestCarriesTheInlineAddressAndEmailToTheOrder() {
        Cart cart = sampleCart();
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(cart));
        when(orderService.createOrder(any(CreateOrderRequestDto.class), eq(GUEST)))
                .thenReturn(GetOrderResponseDto.builder().id(12L).orderStatus(OrderStatus.PENDING).build());
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setEmail("guest@example.com");
        request.setShipping(ShippingAddressRequestDto.builder()
                .recipientName("Guest Shopper").phone("9876543210").line1("1 Market Road")
                .city("Bengaluru").state("Karnataka").postalCode("560001").build());

        cartService.checkout(GUEST, request);

        ArgumentCaptor<CreateOrderRequestDto> captor = ArgumentCaptor.forClass(CreateOrderRequestDto.class);
        verify(orderService).createOrder(captor.capture(), eq(GUEST));
        assertEquals("guest@example.com", captor.getValue().getEmail());
        assertEquals("Bengaluru", captor.getValue().getShipping().getCity());
        // An account-only lookup must never run for a guest, which is the whole point of V29.
        verifyNoInteractions(userRepository);
    }

    @Test
    void checkout_asGuestStillNeedsADestination() {
        Cart cart = sampleCart();
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(cart));

        assertThrows(InvalidRequestException.class, () -> cartService.checkout(GUEST, null));
        verifyNoInteractions(orderService);
    }

    @Test
    void getMyCart_answersAnEmptyCartForANewGuestSession() {
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.empty());

        var result = cartService.getMyCart(GUEST);

        assertEquals(0, result.getItemCount());
        assertNull(result.getUserId(), "a guest cart names no account");
    }

    @Test
    void addItem_createsAGuestCartKeyedOnTheSessionAlone() {
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(call -> call.getArgument(0));
        Product product = Product.builder().title("Organic Cotton Crew Tee").build();
        product.setId(1L);
        ProductVariant variant = ProductVariant.builder().sku("PT-1-M-BLA").size("M").stock(10).build();
        variant.setId(11L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantResolver.resolve(product, 11L)).thenReturn(variant);
        when(variantResolver.unitPrice(product, variant)).thenReturn(java.math.BigDecimal.valueOf(990));

        AddToCartRequestDto request = new AddToCartRequestDto();
        request.setProductId(1L);
        request.setVariantId(11L);
        request.setQuantity(2);
        cartService.addItem(GUEST, request);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, atLeastOnce()).save(captor.capture());
        assertEquals(GUEST_TOKEN, captor.getAllValues().get(0).getGuestToken());
        assertNull(captor.getAllValues().get(0).getUser());
        verifyNoInteractions(userRepository);
    }

    @Test
    void mergeGuestCart_movesLinesTheAccountDoesNotAlreadyHave() {
        Cart mine = sampleCart();
        Cart theirs = guestCartWith(21, 2, 10);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(mine));
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(theirs));

        var result = cartService.mergeGuestCart(GUEST_TOKEN, 1L);

        assertEquals(2, mine.getItems().size(), "a different variant is its own line");
        assertEquals(5, result.getItemCount());
        verify(cartRepository).delete(theirs);
    }

    @Test
    void mergeGuestCart_sumsARepeatedLineAndRetiresTheGuestCart() {
        Cart mine = sampleCart();
        Cart theirs = sampleCart();
        theirs.setGuestToken(GUEST_TOKEN);
        theirs.setId(9L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(mine));
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(theirs));

        var result = cartService.mergeGuestCart(GUEST_TOKEN, 1L);

        assertEquals(1, mine.getItems().size(), "the same product and variant stays one line");
        assertEquals(6, result.getItemCount(), "3 already in the cart plus 3 from the session");
        verify(cartRepository).delete(theirs);
    }

    @Test
    void mergeGuestCart_capsAtWhatIsSellableRatherThanFailingSignIn() {
        Cart mine = sampleCart();
        Cart theirs = guestCartWith(11, 90, 4);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(mine));
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(theirs));

        var result = cartService.mergeGuestCart(GUEST_TOKEN, 1L);

        assertEquals(4, result.getItemCount(), "3 in the cart plus one more, capped at 4 in stock");
    }

    @Test
    void mergeGuestCart_dropsALineThatSoldOutBeforeSignIn() {
        Cart mine = sampleCart();
        Cart theirs = guestCartWith(21, 1, 0);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(mine));
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(theirs));

        var result = cartService.mergeGuestCart(GUEST_TOKEN, 1L);

        assertEquals(1, mine.getItems().size());
        assertEquals(3, result.getItemCount());
    }

    @Test
    void mergeGuestCart_withoutASessionLeavesTheCartAlone() {
        Cart mine = sampleCart();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(mine));

        var result = cartService.mergeGuestCart(null, 1L);

        assertEquals(3, result.getItemCount());
        verify(cartRepository, never()).delete(any(Cart.class));
        verify(cartRepository, never()).findByGuestToken(any());
    }

    @Test
    void checkout_emptiesTheGuestCartThroughTheSamePathAsAnAccount() {
        Cart cart = sampleCart();
        cart.setUser(null);
        cart.setGuestToken(GUEST_TOKEN);
        when(cartRepository.findByGuestToken(GUEST_TOKEN)).thenReturn(Optional.of(cart));
        when(orderService.createOrder(any(CreateOrderRequestDto.class), eq(GUEST)))
                .thenReturn(GetOrderResponseDto.builder().id(12L).orderStatus(OrderStatus.PENDING).build());
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setEmail("guest@example.com");
        request.setShipping(ShippingAddressRequestDto.builder()
                .recipientName("Guest Shopper").phone("9876543210").line1("1 Market Road")
                .city("Bengaluru").state("Karnataka").postalCode("560001").build());

        cartService.checkout(GUEST, request);

        assertTrue(cart.getItems().isEmpty(), "a placed order empties the cart");
    }
}
