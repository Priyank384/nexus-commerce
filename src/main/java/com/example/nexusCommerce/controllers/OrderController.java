package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.security.IdempotencyService;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Orders", description = "Place and manage orders")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotency;

    private static final String IDEM_SCOPE = "orders";

    @Operation(summary = "List all orders",
               description = "Paged list across all users. Requires ROLE_ADMIN.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getAllOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetOrderResponseDto> orders = orderService.getAllOrders(page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @Operation(summary = "Place an order",
               description = "Creates an order for the authenticated caller and atomically reserves stock for each line item. "
                             + "Send an Idempotency-Key header to make retries safe: a repeat of the same key replays the original response.")
    @PostMapping
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> createOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        boolean useIdem = idempotencyKey != null && !idempotencyKey.isBlank();
        if (useIdem) {
            var cached = idempotency.tryStart(caller.id(), IDEM_SCOPE, idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(cached.get());
            }
        }
        GetOrderResponseDto order;
        try {
            order = orderService.createOrder(requestDto, caller.id());
        } catch (RuntimeException ex) {
            if (useIdem) idempotency.release(caller.id(), IDEM_SCOPE, idempotencyKey);
            throw ex;
        }
        ApiResponse<GetOrderResponseDto> body =
                ApiResponse.success(order, "Order Created Successfully");
        if (useIdem) idempotency.complete(caller.id(), IDEM_SCOPE, idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Delete an order",
               description = "Soft-deletes the order and returns reserved stock to its line-item products. Requires ROLE_ADMIN.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(null, "Order Deleted Successfully"));
    }

    @Operation(summary = "Get an order by id",
               description = "Owner or ROLE_ADMIN only. Returns the order with its snapshot line items.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> getOrderById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto order = orderService.getOrderById(id);
        assertCanAccess(order, caller);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(order, "Order Fetched Successfully!"));
    }

    @Operation(summary = "List orders for a user",
               description = "Owner or ROLE_ADMIN only. Paged.")
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getOrdersByUser(
            @PathVariable Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(userId)) {
            throw new ForbiddenException("You can only view your own orders");
        }
        PageResponse<GetOrderResponseDto> orders =
                orderService.getOrdersByUserId(userId, page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @Operation(summary = "List my orders",
               description = "Paged orders belonging to the authenticated caller.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getMyOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        PageResponse<GetOrderResponseDto> orders =
                orderService.getOrdersByUserId(caller.id(), page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @Operation(summary = "Update an order",
               description = "Owner or ROLE_ADMIN only. An owner may apply ADD/REMOVE/INCREMENT/DECREMENT "
                           + "while the order is PENDING and unpaid; status changes and edits to a PAID order "
                           + "require ROLE_ADMIN. Status moves PENDING→SHIPPED→DELIVERED or PENDING→CANCELLED, "
                           + "and cancelling returns the reserved stock. Blocked once DELIVERED or CANCELLED.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> updateOrder(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto current = orderService.getOrderById(id);
        assertCanAccess(current, caller);
        return ResponseEntity.status(HttpStatus.OK)
            .body(ApiResponse.success(
                    orderService.updateOrder(id, requestDto, caller.id(), caller.isAdmin()),
                    "Order Updated Successfully"));
    }

    @Operation(summary = "Get an order summary",
               description = "Owner or ROLE_ADMIN only. Aggregated totals derived from the order's line-item snapshots.")
    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<GetOrderSummaryResponseDto>> getOrderSummary(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto order = orderService.getOrderById(id);
        assertCanAccess(order, caller);
        GetOrderSummaryResponseDto summary = orderService.getOrderSummary(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(summary, "Order Summary Fetched Successfully"));
    }

    private void assertCanAccess(GetOrderResponseDto order, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(order.getUserId())) {
            throw new ForbiddenException("You can only access your own orders");
        }
    }
}
