package com.example.nexusCommerce.adapters;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;

@Component
public class OrderAdapter {

    // Pure mapping: the caller supplies the line items so no query runs per order while
    // a list is rendered.
    public List<GetOrderResponseDto> mapToGetOrderResponseDtoList(List<Order> orders,
                                                                 Map<Long, List<OrderProducts>> itemsByOrderId) {
        return orders.stream()
                .map(order -> mapToGetOrderResponseDto(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .collect(Collectors.toList());
    }

    public GetOrderResponseDto mapToGetOrderResponseDto(Order order, List<OrderProducts> orderProducts) {
        return GetOrderResponseDto.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .orderStatus(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(mapToOrderItemResponseDto(orderProducts))
                .build();
    }

    public List<OrderItemResponseDto> mapToOrderItemResponseDto(List<OrderProducts> orderProducts) {
        return orderProducts.stream()
                .map(op -> OrderItemResponseDto.builder()
                        .productId(op.getProductId())
                        .quantity(op.getQuantity())
                        .productName(op.getProductName())
                        .productPrice(op.getUnitPrice())
                        .productImage(op.getProductImage())
                        .variantId(op.getVariantId())
                        .variantSize(op.getVariantSize())
                        .variantColorName(op.getVariantColorName())
                        .variantColorHex(op.getVariantColorHex())
                        .subTotal(op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity())))
                        .build()).collect(Collectors.toList());
    }
}
