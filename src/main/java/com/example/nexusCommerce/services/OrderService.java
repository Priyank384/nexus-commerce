package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.utils.PageResponse;

public interface OrderService {
    PageResponse<GetOrderResponseDto> getAllOrders(int page, int size);
    PageResponse<GetOrderResponseDto> getOrdersByUserId(Long userId, int page, int size);
    GetOrderResponseDto getOrderById(Long id);
    GetOrderResponseDto createOrder(CreateOrderRequestDto createOrderRequestDto, Long authenticatedUserId);
    GetOrderResponseDto updateOrder(Long id, UpdateOrderRequestDto updateOrderRequestDto,
            Long callerId, boolean callerIsAdmin);
    GetOrderSummaryResponseDto getOrderSummary(Long id);
    void deleteOrder(Long id);
}
