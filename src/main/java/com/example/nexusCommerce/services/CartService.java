package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CartResponseDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.UpdateCartItemRequestDto;
import com.example.nexusCommerce.security.ShopperIdentity;

/**
 * The cart, for either kind of shopper. Every method is keyed by a {@link ShopperIdentity}
 * because the row a cart belongs to is resolved from an account or a guest session, and the
 * rest of the behaviour is identical.
 */
public interface CartService {
    /** Returns the caller's cart, creating an empty one on first access. */
    CartResponseDto getMyCart(ShopperIdentity shopper);
    /** Adds the product to the cart; if already present, sums the quantities. */
    CartResponseDto addItem(ShopperIdentity shopper, AddToCartRequestDto requestDto);
    /** Replaces the quantity of an existing line item. */
    CartResponseDto updateItem(ShopperIdentity shopper, Long itemId, UpdateCartItemRequestDto requestDto);
    void removeItem(ShopperIdentity shopper, Long itemId);
    void clear(ShopperIdentity shopper);
    /** Converts the cart into an Order and empties it. Returns the created order. */
    GetOrderResponseDto checkout(ShopperIdentity shopper, CreateOrderRequestDto override);
    /**
     * Moves a guest cart's lines onto the signing-in account's cart and retires the guest cart.
     * Called after login, which is the moment a storefront learns the anonymous browser and the
     * account are the same person.
     */
    CartResponseDto mergeGuestCart(String guestToken, Long userId);
}
