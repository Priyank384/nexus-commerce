package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CartResponseDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.UpdateCartItemRequestDto;

public interface CartService {
    /** Returns the caller's cart, creating an empty one on first access. */
    CartResponseDto getMyCart(Long userId);
    /** Adds the product to the cart; if already present, sums the quantities. */
    CartResponseDto addItem(Long userId, AddToCartRequestDto requestDto);
    /** Replaces the quantity of an existing line item. */
    CartResponseDto updateItem(Long userId, Long itemId, UpdateCartItemRequestDto requestDto);
    void removeItem(Long userId, Long itemId);
    void clear(Long userId);
    /** Converts the cart into an Order and empties it. Returns the created order. */
    GetOrderResponseDto checkout(Long userId, CreateOrderRequestDto override);
}
