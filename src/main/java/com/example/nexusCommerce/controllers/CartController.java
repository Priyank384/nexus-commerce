package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.AddToCartRequestDto;
import com.example.nexusCommerce.dtos.CartResponseDto;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.UpdateCartItemRequestDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.security.IdempotencyService;
import com.example.nexusCommerce.services.CartService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cart", description = "Persistent shopping cart per authenticated user")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final IdempotencyService idempotency;

    private static final String CHECKOUT_IDEM_SCOPE = "cart-checkout";

    @Operation(summary = "Get my cart",
               description = "Returns the caller's cart; creates an empty one on first access.")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponseDto>> getMyCart(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(caller.id()), "Cart fetched"));
    }

    @Operation(summary = "Add a product to the cart",
               description = "Inserts the item or increases the quantity of an existing line for the same product.")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponseDto>> addItem(
            @Valid @RequestBody AddToCartRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cartService.addItem(caller.id(), requestDto), "Item added to cart"));
    }

    @Operation(summary = "Set quantity of a cart line",
               description = "Replaces the quantity on an existing line. Owner-only.")
    @PatchMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponseDto>> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItem(caller.id(), id, requestDto), "Cart item updated"));
    }

    @Operation(summary = "Remove a line from the cart",
               description = "Owner-only.")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<Void>> removeItem(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthenticatedUser caller) {
        cartService.removeItem(caller.id(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Cart item removed"));
    }

    @Operation(summary = "Empty the cart",
               description = "Removes every line item but keeps the cart itself.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clear(@AuthenticationPrincipal AuthenticatedUser caller) {
        cartService.clear(caller.id());
        return ResponseEntity.ok(ApiResponse.success(null, "Cart cleared"));
    }

    @Operation(summary = "Checkout the cart",
               description = "Converts the cart into an order (stock reserved atomically, snapshots written) and empties the cart. "
                             + "Optionally send a body to override the line items; omit it to use the cart as-is. "
                             + "Send an Idempotency-Key header to make retries safe: a repeat of the same key replays the first response "
                             + "instead of placing a duplicate order.")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> checkout(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateOrderRequestDto override,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        boolean useIdem = idempotencyKey != null && !idempotencyKey.isBlank();
        if (useIdem) {
            var cached = idempotency.tryStart(caller.id(), CHECKOUT_IDEM_SCOPE, idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(cached.get());
            }
        }
        GetOrderResponseDto order;
        try {
            order = cartService.checkout(caller.id(), override);
        } catch (RuntimeException ex) {
            if (useIdem) idempotency.release(caller.id(), CHECKOUT_IDEM_SCOPE, idempotencyKey);
            throw ex;
        }
        ApiResponse<GetOrderResponseDto> body = ApiResponse.success(order, "Order placed from cart");
        if (useIdem) idempotency.complete(caller.id(), CHECKOUT_IDEM_SCOPE, idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
