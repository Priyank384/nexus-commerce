package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CartItemResponseDto;
import com.example.nexusCommerce.dtos.CartResponseDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
import com.example.nexusCommerce.dtos.ShippingAddressRequestDto;
import com.example.nexusCommerce.dtos.UpdateCartItemRequestDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CartRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Cart;
import com.example.nexusCommerce.schema.CartItem;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.ShopperIdentity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    /**
     * A cart line is a shopping intent, not a wholesale order. Capping it also keeps
     * alreadyInCart + quantity from overflowing an int (a live audit reached -2147483648
     * through a client-supplied Integer.MAX_VALUE).
     */
    private static final int MAX_LINE_QUANTITY = 99;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final VariantResolver variantResolver;

    @Transactional(readOnly = true)
    @Override
    public CartResponseDto getMyCart(ShopperIdentity shopper) {
        return findCart(shopper)
                .map(cart -> toResponse(cart, shopper))
                .orElseGet(() -> emptyCart(shopper));
    }

    @Transactional
    @Override
    public CartResponseDto addItem(ShopperIdentity shopper, AddToCartRequestDto requestDto) {
        Cart cart = findOrCreate(shopper);
        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + requestDto.getProductId()));
        ProductVariant variant = variantResolver.resolve(product, requestDto.getVariantId());

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> variant.getId().equals(lineVariantId(i)))
                .findFirst();
        int alreadyInCart = existing.map(CartItem::getQuantity).orElse(0);
        int targetQuantity = checkedLineQuantity(alreadyInCart, requestDto.getQuantity());
        assertStockCovers(variant, targetQuantity);

        if (existing.isPresent()) {
            existing.get().setQuantity(targetQuantity);
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(requestDto.getQuantity())
                    .build());
        }
        return toResponse(cartRepository.save(cart), shopper);
    }

    @Transactional
    @Override
    public CartResponseDto updateItem(ShopperIdentity shopper, Long itemId, UpdateCartItemRequestDto requestDto) {
        Cart cart = findOrCreate(shopper);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        // The quantity is replaced, not added, but it still has to be sellable: a cart kept
        // at four units of a two-unit variant would fail at checkout, after the shopper has
        // already committed to the order.
        int targetQuantity = checkedLineQuantity(0, requestDto.getQuantity());
        assertStockCovers(item.getVariant(), targetQuantity);
        item.setQuantity(targetQuantity);
        return toResponse(cartRepository.save(cart), shopper);
    }

    @Transactional
    @Override
    public void removeItem(ShopperIdentity shopper, Long itemId) {
        Cart cart = findOrCreate(shopper);
        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw new ResourceNotFoundException("Cart item not found: " + itemId);
        cartRepository.save(cart);
    }

    @Transactional
    @Override
    public void clear(ShopperIdentity shopper) {
        Cart cart = findOrCreate(shopper);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    @Override
    public GetOrderResponseDto checkout(ShopperIdentity shopper, CreateOrderRequestDto override) {
        Cart cart = findOrCreate(shopper);
        if (cart.getItems().isEmpty()) {
            throw new InvalidRequestException("Cart is empty");
        }
        // An order without a destination cannot be fulfilled, so checkout refuses rather than
        // silently placing one with a null snapshot. A guest's destination is the inline form;
        // an account's is either that or a saved address id.
        Long addressId = override == null ? null : override.getAddressId();
        ShippingAddressRequestDto shipping = override == null ? null : override.getShipping();
        if (addressId == null && shipping == null) {
            throw new InvalidRequestException("A shipping address is required to place an order");
        }
        List<OrderItemRequestDto> items = (override != null && override.getOrderItems() != null
                && !override.getOrderItems().isEmpty())
                ? override.getOrderItems()
                // The variant travels with the line: ordering by product id alone would make
                // OrderServiceImpl reject every multi-variant garment.
                : cart.getItems().stream()
                        .map(i -> OrderItemRequestDto.builder()
                                .productId(i.getProduct().getId())
                                .variantId(lineVariantId(i))
                                .quantity(i.getQuantity())
                                .build())
                        .toList();
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setOrderItems(items);
        request.setAddressId(addressId);
        request.setShipping(shipping);
        request.setEmail(override == null ? null : override.getEmail());
        GetOrderResponseDto order = orderService.createOrder(request, shopper);
        cart.getItems().clear();
        cartRepository.save(cart);
        return order;
    }

    /**
     * Sign-in is the moment the storefront learns that the anonymous browser and the account are
     * the same person, and the guest's basket is the thing at risk of being lost.
     *
     * <p>Lines that no longer fit are clamped or dropped instead of failing the call: a merge
     * happens immediately after a successful login, and an exception there would look like the
     * sign-in itself broke. The account's own quantities win over the guest's, and the guest
     * cart is soft-deleted so the session starts clean.
     */
    @Transactional
    @Override
    public CartResponseDto mergeGuestCart(String guestToken, Long userId) {
        ShopperIdentity shopper = new ShopperIdentity(userId, null);
        Cart target = findOrCreate(shopper);
        Cart guest = guestToken == null ? null
                : cartRepository.findByGuestToken(guestToken).orElse(null);
        if (guest == null || guest.getItems().isEmpty()) {
            return toResponse(target, shopper);
        }
        for (CartItem line : List.copyOf(guest.getItems())) {
            int wanted = line.getQuantity();
            ProductVariant variant = line.getVariant();
            int available = variant == null || variant.getStock() == null ? 0 : variant.getStock();
            Optional<CartItem> sameLine = target.getItems().stream()
                    .filter(t -> java.util.Objects.equals(lineVariantId(t), lineVariantId(line)))
                    .findFirst();
            int existingQuantity = sameLine.map(CartItem::getQuantity).orElse(0);
            int merged = Math.min(Math.min(existingQuantity + wanted, MAX_LINE_QUANTITY), available);
            if (merged == 0) {
                log.info("Guest cart line {} sold out before the merge and was dropped", lineVariantId(line));
                continue;
            }
            if (sameLine.isPresent()) {
                sameLine.get().setQuantity(merged);
            } else {
                target.getItems().add(CartItem.builder()
                        .cart(target)
                        .product(line.getProduct())
                        .variant(variant)
                        .quantity(merged)
                        .build());
            }
            if (merged < existingQuantity + wanted) {
                log.info("Guest cart line {} capped from {} to {} units on merge",
                        lineVariantId(line), existingQuantity + wanted, merged);
            }
        }
        cartRepository.save(target);
        cartRepository.delete(guest);
        return toResponse(target, shopper);
    }

    /**
     * The cart a shopper already has, if any. Both identities are one lookup: an account by its
     * row, a guest by the session credential it carries.
     */
    private Optional<Cart> findCart(ShopperIdentity shopper) {
        return shopper.isGuest()
                ? cartRepository.findByGuestToken(shopper.guestToken())
                : cartRepository.findByUserId(shopper.userId());
    }

    private Cart findOrCreate(ShopperIdentity shopper) {
        return findCart(shopper).orElseGet(() -> {
            Cart fresh = shopper.isGuest()
                    ? Cart.builder().guestToken(shopper.guestToken()).build()
                    : Cart.builder().user(referenceUser(shopper.userId())).build();
            return cartRepository.save(fresh);
        });
    }

    /** Only the cart's own row needs the association; the guest path has no user to look up. */
    private User referenceUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private CartResponseDto toResponse(Cart cart, ShopperIdentity shopper) {
        List<CartItemResponseDto> lines = cart.getItems().stream()
                .map(i -> {
                    Product product = i.getProduct();
                    ProductVariant variant = i.getVariant();
                    BigDecimal unit = variantResolver.unitPrice(product, variant);
                    BigDecimal qty = BigDecimal.valueOf(i.getQuantity());
                    return CartItemResponseDto.builder()
                            .id(i.getId())
                            .productId(product.getId())
                            .productName(product.getTitle())
                            .productImage(variant.getImageUrl() != null
                                    ? variant.getImageUrl() : product.getImage())
                            .unitPrice(unit)
                            .quantity(i.getQuantity())
                            .lineTotal(unit.multiply(qty))
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .variantSize(variant.getSize())
                            .variantColorName(variant.getColorName())
                            .variantColorHex(variant.getColorHex())
                            .variantStock(variant.getStock())
                            .build();
                })
                .toList();
        BigDecimal total = lines.stream()
                .map(CartItemResponseDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = lines.stream().mapToInt(CartItemResponseDto::getQuantity).sum();
        return CartResponseDto.builder()
                .id(cart.getId())
                // The caller's id, not cart.getUser().getId(): the user is a lazy association
                // and the graph that loaded this cart did not fetch it. Null for a guest cart,
                // whose identity is the session token the client already holds.
                .userId(shopper.userId())
                .items(lines)
                .grandTotal(total)
                .itemCount(count)
                .build();
    }

    private CartResponseDto emptyCart(ShopperIdentity shopper) {
        return CartResponseDto.builder()
                .userId(shopper.userId())
                .items(List.of())
                .grandTotal(BigDecimal.ZERO)
                .itemCount(0)
                .build();
    }

    private static void assertStockCovers(ProductVariant variant, int quantity) {
        int available = variant.getStock() == null ? 0 : variant.getStock();
        if (quantity > available) {
            throw new InvalidRequestException("Only " + available + " left of "
                    + variant.getSize() + " / " + variant.getColorName());
        }
    }

    private static int checkedLineQuantity(int alreadyInCart, Integer requested) {
        if (requested == null || requested < 1) {
            throw new InvalidRequestException("Cart quantity must be at least 1");
        }
        long total = (long) alreadyInCart + requested;
        if (total > MAX_LINE_QUANTITY) {
            throw new InvalidRequestException(
                    "A cart line cannot hold more than " + MAX_LINE_QUANTITY + " units");
        }
        return (int) total;
    }

    // A brand-new line has no read-only variant_id mirror until it is flushed.
    private static Long lineVariantId(CartItem item) {
        if (item.getVariantId() != null) {
            return item.getVariantId();
        }
        return item.getVariant() != null ? item.getVariant().getId() : null;
    }
}
