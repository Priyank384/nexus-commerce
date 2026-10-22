package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.AddToWishlistRequestDto;
import com.example.nexusCommerce.dtos.CreateWishlistRequestDto;
import com.example.nexusCommerce.dtos.GetWishlistResponseDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.WishlistService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Every route here is owner-only: the service compares the caller's id with the list's, so a
 * guessed wishlist id reads as missing rather than as somebody else's saved products.
 */
@Tag(name = "Wishlist", description = "Saved products per authenticated user")
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "Get my default wishlist",
               description = "The list the storefront heart reads. Created empty on first access, and its items "
                           + "are resolved against the current catalogue, so a discontinued product disappears "
                           + "rather than rendering as a broken card.")
    @GetMapping
    public ResponseEntity<ApiResponse<GetWishlistResponseDto>> getMyWishlist(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                wishlistService.getMyWishlist(caller.id()), "Wishlist fetched"));
    }

    @Operation(summary = "List my wishlists",
               description = "Every list the caller owns, with item counts but without items -- the account page "
                           + "shows these and fetches one by id.")
    @GetMapping("/lists")
    public ResponseEntity<ApiResponse<List<GetWishlistResponseDto>>> listMyWishlists(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                wishlistService.listMyWishlists(caller.id()), "Wishlists fetched"));
    }

    @Operation(summary = "Create a named wishlist",
               description = "A second list (Honeymoon, Gifts). Names are unique per user.")
    @PostMapping
    public ResponseEntity<ApiResponse<GetWishlistResponseDto>> create(
            @Valid @RequestBody CreateWishlistRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                wishlistService.create(caller.id(), requestDto), "Wishlist created"));
    }

    @Operation(summary = "Delete a wishlist",
               description = "Soft-deletes the list and stops its items from being read. Owner-only.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        wishlistService.delete(caller.id(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Wishlist deleted"));
    }

    @Operation(summary = "Add to my default wishlist",
               description = "A product can be saved once per list; adding it again with a different size+colour "
                           + "restates the existing line instead of duplicating it. Owner-only.")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<GetWishlistResponseDto>> addToDefault(
            @Valid @RequestBody AddToWishlistRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                wishlistService.addItem(caller.id(), null, requestDto), "Saved to wishlist"));
    }

    @Operation(summary = "Remove from my default wishlist",
               description = "Owner-only.")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromDefault(@PathVariable Long productId,
                                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        wishlistService.removeItem(caller.id(), null, productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Removed from wishlist"));
    }

    @Operation(summary = "Add to one of my wishlists", description = "Owner-only.")
    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<GetWishlistResponseDto>> addTo(
            @PathVariable Long id,
            @Valid @RequestBody AddToWishlistRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                wishlistService.addItem(caller.id(), id, requestDto), "Saved to wishlist"));
    }

    @Operation(summary = "Remove from one of my wishlists", description = "Owner-only.")
    @DeleteMapping("/{id}/items/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFrom(@PathVariable Long id, @PathVariable Long productId,
                                                        @AuthenticationPrincipal AuthenticatedUser caller) {
        wishlistService.removeItem(caller.id(), id, productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Removed from wishlist"));
    }
}
