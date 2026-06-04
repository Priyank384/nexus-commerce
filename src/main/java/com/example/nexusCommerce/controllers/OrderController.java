package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.updateOrderRequestDto;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GetOrderResponseDto>>> getAllOrders() {
        List<GetOrderResponseDto> orders = orderService.getAllOrders();
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orders, "Orders Fetched Successfully!"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> createOrder(@RequestBody CreateOrderRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(orderService.createOrder(requestDto), "Order Created Successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null, "Order Deleted Successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> getOrderById(@PathVariable Long id) {
        GetOrderResponseDto order = orderService.getOrderById(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(order, "Order Fetched Successfully!"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetOrderResponseDto>> updateOrder(@PathVariable Long id, @RequestBody updateOrderRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(orderService.updateOrder(id, requestDto), "Order Updated Successfully"));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<GetOrderSummaryResponseDto>> getOrderSummary(@PathVariable Long id) {
        GetOrderSummaryResponseDto summary = orderService.getOrderSummary(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(summary, "Order Summary Fetched Successfully"));
    }

}
