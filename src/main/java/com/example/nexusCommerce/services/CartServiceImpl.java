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
import com.example.nexusCommerce.schema.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    @Override
    public CartResponseDto getMyCart(Long userId) {
        return toResponse(findOrCreate(userId));
    }

    @Transactional
    @Override
    public CartResponseDto addItem(Long userId, AddToCartRequestDto requestDto) {
        Cart cart = findOrCreate(userId);
        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + requestDto.getProductId()));
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(product.getId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + requestDto.getQuantity());
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(requestDto.getQuantity())
                    .build());
        }
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    @Override
    public CartResponseDto updateItem(Long userId, Long itemId, UpdateCartItemRequestDto requestDto) {
        Cart cart = findOrCreate(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        item.setQuantity(requestDto.getQuantity());
        return toResponse(cartRepository.save(cart));
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
        List<OrderItemRequestDto> items = (override != null && override.getOrderItems() != null
                && !override.getOrderItems().isEmpty())
                ? override.getOrderItems()
                : cart.getItems().stream()
                        .map(i -> new OrderItemRequestDto(i.getProductId(), i.getQuantity()))
                        .toList();
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setOrderItems(items);
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

    private CartResponseDto toResponse(Cart cart) {
        List<CartItemResponseDto> lines = cart.getItems().stream()
                .map(i -> {
                    BigDecimal unit = i.getProduct().getPrice();
                    BigDecimal qty = BigDecimal.valueOf(i.getQuantity());
                    return CartItemResponseDto.builder()
                            .id(i.getId())
                            .productId(i.getProductId())
                            .productName(i.getProduct().getTitle())
                            .productImage(i.getProduct().getImage())
                            .unitPrice(unit)
                            .quantity(i.getQuantity())
                            .lineTotal(unit.multiply(qty))
                            .build();
                })
                .toList();
        BigDecimal total = lines.stream()
                .map(CartItemResponseDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = lines.stream().mapToInt(CartItemResponseDto::getQuantity).sum();
        return CartResponseDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(lines)
                .grandTotal(total)
                .itemCount(count)
                .build();
    }
}
