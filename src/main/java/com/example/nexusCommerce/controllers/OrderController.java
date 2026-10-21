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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Orders", description = "Place and manage orders")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getAllOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetOrderResponseDto> orders = orderService.getAllOrders(page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> createOrder(
            @Valid @RequestBody CreateOrderRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto order = orderService.createOrder(requestDto, caller.id());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(order, "Order Created Successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null, "Order Deleted Successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> getOrderById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto order = orderService.getOrderById(id);
        assertCanAccess(order, caller);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(order, "Order Fetched Successfully!"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getOrdersByUser(
            @PathVariable Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(userId)) {
            throw new InvalidRequestException("You can only view your own orders");
        }
        PageResponse<GetOrderResponseDto> orders =
                orderService.getOrdersByUserId(userId, page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<GetOrderResponseDto>>> getMyOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        PageResponse<GetOrderResponseDto> orders =
                orderService.getOrdersByUserId(caller.id(), page, Pagination.clampSize(size));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> updateOrder(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        GetOrderResponseDto current = orderService.getOrderById(id);
        assertCanAccess(current, caller);
        return ResponseEntity.status(HttpStatus.OK)
            .body(ApiResponse.success(orderService.updateOrder(id, requestDto), "Order Updated Successfully"));
    }

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
            throw new InvalidRequestException("You can only access your own orders");
        }
    }
}
