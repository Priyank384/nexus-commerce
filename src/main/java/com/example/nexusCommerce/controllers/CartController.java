package com.example.nexusCommerce.controllers;

import java.util.Optional;

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
import com.example.nexusCommerce.security.ShopperIdentity;
import com.example.nexusCommerce.services.CartService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cart", description = "Persistent shopping cart for an account or a guest session")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final IdempotencyService idempotency;

    private static final String CHECKOUT_IDEM_SCOPE = "cart-checkout";

    /**
     * A bearer token always decides which cart is being read, so a {@code X-Guest-Token} header
     * sent alongside one is ignored rather than given a second cart to act on.
     */
    private static ShopperIdentity resolve(AuthenticatedUser caller, String guestToken) {
        return ShopperIdentity.require(caller, guestToken);
    }

    @Operation(summary = "Get my cart",
               description = "Returns the caller's cart, or an empty one when nothing has been added yet — reading "
                             + "never creates a row. Send " + ShopperIdentity.HEADER + " instead of a bearer token "
                             + "to read a guest cart.")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponseDto>> getMyCart(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(resolve(caller, guestToken)), "Cart fetched"));
    }

    @Operation(summary = "Add a product to the cart",
               description = "Inserts the item or increases the quantity of an existing line for the same product.")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponseDto>> addItem(
            @Valid @RequestBody AddToCartRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cartService.addItem(resolve(caller, guestToken), requestDto), "Item added to cart"));
    }

    @Operation(summary = "Set quantity of a cart line",
               description = "Replaces the quantity on an existing line. Cart-owner only, by token or guest session.")
    @PatchMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponseDto>> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItem(resolve(caller, guestToken), id, requestDto), "Cart item updated"));
    }

    @Operation(summary = "Remove a line from the cart",
               description = "Cart-owner only.")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        cartService.removeItem(resolve(caller, guestToken), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Cart item removed"));
    }

    @Operation(summary = "Empty the cart",
               description = "Removes every line item but keeps the cart itself.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clear(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        cartService.clear(resolve(caller, guestToken));
        return ResponseEntity.ok(ApiResponse.success(null, "Cart cleared"));
    }

    @Operation(summary = "Fold a guest cart into the signed-in account's cart",
               description = "Call it right after a login with the same " + ShopperIdentity.HEADER
                             + " the browser has been using. Lines of the same product and variant are summed, a line "
                             + "that no longer fits what is in stock is capped or dropped rather than failing the call, "
                             + "and the guest cart is retired. No header is a no-op.")
    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<CartResponseDto>> merge(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken) {
        // The destination has to be an account; a cart has nothing to merge into otherwise.
        ShopperIdentity account = ShopperIdentity.require(caller, null);
        Optional<String> session = ShopperIdentity.guestSession(guestToken);
        CartResponseDto cart = cartService.mergeGuestCart(session.orElse(null), account.userId());
        return ResponseEntity.ok(ApiResponse.success(cart,
                session.isEmpty() ? "Cart unchanged" : "Guest cart merged"));
    }

    @Operation(summary = "Checkout the cart",
               description = "Converts the cart into an order (stock reserved atomically, snapshots written) and empties the cart. "
                             + "Optionally send a body to override the line items; omit it to use the cart as-is. "
                             + "A guest sends email and shipping in that body; an account sends addressId. "
                             + "Send an Idempotency-Key header to make retries safe: a repeat of the same key replays the first response "
                             + "instead of placing a duplicate order.")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> checkout(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = ShopperIdentity.HEADER, required = false) String guestToken,
            @RequestBody(required = false) CreateOrderRequestDto override,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        ShopperIdentity shopper = resolve(caller, guestToken);
        boolean useIdem = idempotencyKey != null && !idempotencyKey.isBlank();
        if (useIdem) {
            var cached = idempotency.tryStart(shopper, CHECKOUT_IDEM_SCOPE, idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(cached.get());
            }
        }
        GetOrderResponseDto order;
        try {
            order = cartService.checkout(shopper, override);
        } catch (RuntimeException ex) {
            if (useIdem) idempotency.release(shopper, CHECKOUT_IDEM_SCOPE, idempotencyKey);
            throw ex;
        }
        ApiResponse<GetOrderResponseDto> body = ApiResponse.success(order, "Order placed from cart");
        if (useIdem) idempotency.complete(shopper, CHECKOUT_IDEM_SCOPE, idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
