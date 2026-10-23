package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CartItemResponseDto;
import com.example.nexusCommerce.dtos.CartResponseDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final VariantResolver variantResolver;

    @Transactional(readOnly = true)
    @Override
    public CartResponseDto getMyCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> toResponse(cart, userId))
                .orElseGet(() -> emptyCart(userId));
    }

    @Transactional
    @Override
    public CartResponseDto addItem(Long userId, AddToCartRequestDto requestDto) {
        Cart cart = findOrCreate(userId);
        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + requestDto.getProductId()));
        ProductVariant variant = variantResolver.resolve(product, requestDto.getVariantId());

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> variant.getId().equals(lineVariantId(i)))
                .findFirst();
        int alreadyInCart = existing.map(CartItem::getQuantity).orElse(0);
        assertStockCovers(variant, alreadyInCart + requestDto.getQuantity());

        if (existing.isPresent()) {
            existing.get().setQuantity(alreadyInCart + requestDto.getQuantity());
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(requestDto.getQuantity())
                    .build());
        }
        return toResponse(cartRepository.save(cart), userId);
    }

    @Transactional
    @Override
    public CartResponseDto updateItem(Long userId, Long itemId, UpdateCartItemRequestDto requestDto) {
        Cart cart = findOrCreate(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        // The quantity is replaced, not added, but it still has to be sellable: a cart kept
        // at four units of a two-unit variant would fail at checkout, after the shopper has
        // already committed to the order.
        assertStockCovers(item.getVariant(), requestDto.getQuantity());
        item.setQuantity(requestDto.getQuantity());
        return toResponse(cartRepository.save(cart), userId);
    }

    @Transactional
    @Override
    public void removeItem(Long userId, Long itemId) {
        Cart cart = findOrCreate(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw new ResourceNotFoundException("Cart item not found: " + itemId);
        cartRepository.save(cart);
    }

    @Transactional
    @Override
    public void clear(Long userId) {
        Cart cart = findOrCreate(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    @Override
    public GetOrderResponseDto checkout(Long userId, CreateOrderRequestDto override) {
        Cart cart = findOrCreate(userId);
        if (cart.getItems().isEmpty()) {
            throw new InvalidRequestException("Cart is empty");
        }
        // An order without a destination cannot be fulfilled, so checkout refuses rather than
        // silently placing one with a null snapshot.
        Long addressId = override == null ? null : override.getAddressId();
        if (addressId == null) {
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
        GetOrderResponseDto order = orderService.createOrder(request, userId);
        cart.getItems().clear();
        cartRepository.save(cart);
        return order;
    }

    private Cart findOrCreate(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            Cart fresh = Cart.builder().user(user).build();
            return cartRepository.save(fresh);
        });
    }

    private CartResponseDto toResponse(Cart cart, Long userId) {
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
                // and the graph that loaded this cart did not fetch it.
                .userId(userId)
                .items(lines)
                .grandTotal(total)
                .itemCount(count)
                .build();
    }

    private CartResponseDto emptyCart(Long userId) {
        return CartResponseDto.builder()
                .userId(userId)
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

    // A brand-new line has no read-only variant_id mirror until it is flushed.
    private static Long lineVariantId(CartItem item) {
        if (item.getVariantId() != null) {
            return item.getVariantId();
        }
        return item.getVariant() != null ? item.getVariant().getId() : null;
    }
}
