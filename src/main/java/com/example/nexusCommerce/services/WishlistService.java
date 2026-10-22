package com.example.nexusCommerce.services;

import java.util.List;

import com.example.nexusCommerce.dtos.AddToWishlistRequestDto;
import com.example.nexusCommerce.dtos.CreateWishlistRequestDto;
import com.example.nexusCommerce.dtos.GetWishlistResponseDto;

public interface WishlistService {
    /** The caller's default list, created on first use -- what the storefront heart reads. */
    GetWishlistResponseDto getMyWishlist(Long userId);
    List<GetWishlistResponseDto> listMyWishlists(Long userId);
    GetWishlistResponseDto create(Long userId, CreateWishlistRequestDto requestDto);
    void delete(Long userId, Long wishlistId);
    /** {@code wishlistId} null means the default list. */
    GetWishlistResponseDto addItem(Long userId, Long wishlistId, AddToWishlistRequestDto requestDto);
    void removeItem(Long userId, Long wishlistId, Long productId);
}
