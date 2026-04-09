package com.example.nexusCommerce.adapters;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderAdapter {

    private final OrderProductsRepository orderProductsRepository;

    public List<GetOrderResponseDto> mapToGetOrderResponseDtoList(List<Order> orders){
        return orders.stream()
                .map(order -> mapToGetOrderResponseDto(order))
                .collect(Collectors.toList());
    }
    
    public GetOrderResponseDto mapToGetOrderResponseDto(Order order){

        List<OrderProducts> orderProducts = orderProductsRepository.findByOrderId(order.getId());
        List<OrderItemResponseDto> items = mapToOrderItemResponseDto(orderProducts);

        return GetOrderResponseDto.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
        
    }

    public List<OrderItemResponseDto> mapToOrderItemResponseDto(List<OrderProducts> orderProducts){
        
        return orderProducts.stream()
                .map(op -> OrderItemResponseDto.builder()
                        .productId(op.getProduct().getId())
                        .quantity(op.getQuantity())
                        .productName(op.getProduct().getTitle())
                        .productPrice(op.getProduct().getPrice())
                        .productImage(op.getProduct().getImage())
                        .subTotal(op.getProduct().getPrice().multiply(BigDecimal.valueOf(op.getQuantity())))
                        .build()).collect(Collectors.toList());
    }
}
