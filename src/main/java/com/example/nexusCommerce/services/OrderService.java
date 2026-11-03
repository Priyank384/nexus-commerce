package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.security.ShopperIdentity;
import com.example.nexusCommerce.utils.PageResponse;

public interface OrderService {
    PageResponse<GetOrderResponseDto> getAllOrders(int page, int size);
    PageResponse<GetOrderResponseDto> getOrdersByUserId(Long userId, int page, int size);
    GetOrderResponseDto getOrderById(Long id);
    /** Places an order for an account or for a guest session; a guest one carries a claim link. */
    GetOrderResponseDto createOrder(CreateOrderRequestDto createOrderRequestDto, ShopperIdentity shopper);
    /** Moves an order placed anonymously onto the caller's account, retiring its claim link. */
    GetOrderResponseDto claimOrder(String claimToken, Long userId);
    GetOrderResponseDto updateOrder(Long id, UpdateOrderRequestDto updateOrderRequestDto,
            Long callerId, boolean callerIsAdmin);
    GetOrderSummaryResponseDto getOrderSummary(Long id);
    void deleteOrder(Long id);
}
