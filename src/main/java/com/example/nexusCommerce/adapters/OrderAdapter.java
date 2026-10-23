package com.example.nexusCommerce.adapters;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetOrderAddressResponseDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.schema.AddressSnapshot;
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
                .totalPrice(totalOf(orderProducts))
                .paymentStatus(order.getPaymentStatus())
                .paidAt(order.getPaidAt())
                .address(addressOf(order.getAddressSnapshot()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(mapToOrderItemResponseDto(orderProducts))
                .build();
    }

    private static GetOrderAddressResponseDto addressOf(AddressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return GetOrderAddressResponseDto.builder()
                .recipientName(snapshot.getRecipientName())
                .phone(snapshot.getPhone())
                .line1(snapshot.getLine1())
                .line2(snapshot.getLine2())
                .city(snapshot.getCity())
                .state(snapshot.getState())
                .postalCode(snapshot.getPostalCode())
                .country(snapshot.getCountry())
                .build();
    }

    /**
     * Summed from the line items already in hand rather than read from a column: the order
     * table has no total, and a second aggregate query per rendered order would be paid by
     * every list endpoint to reproduce arithmetic the snapshots already contain.
     */
    private static BigDecimal totalOf(List<OrderProducts> orderProducts) {
        return orderProducts.stream()
                .map(op -> op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<OrderItemResponseDto> mapToOrderItemResponseDto(List<OrderProducts> orderProducts) {
        return orderProducts.stream()
                .map(op -> OrderItemResponseDto.builder()
                        .productId(productIdOf(op))
                        .quantity(op.getQuantity())
                        .productName(op.getProductName())
                        .productPrice(op.getUnitPrice())
                        .productImage(op.getProductImage())
                        .variantId(variantIdOf(op))
                        .variantSize(op.getVariantSize())
                        .variantColorName(op.getVariantColorName())
                        .variantColorHex(op.getVariantColorHex())
                        .subTotal(op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity())))
                        .build()).collect(Collectors.toList());
    }

    /**
     * product_id and variant_id are insertable=false views of their associations, so a line
     * written moments ago (checkout, place order, add-to-order) has no scalar populated yet and
     * would answer with nulls the storefront needs for "buy again". Reading the identifier off
     * the association is free even for a lazy proxy, and for rows read from the database the
     * scalar is already there.
     */
    private static Long productIdOf(OrderProducts line) {
        if (line.getProductId() != null) {
            return line.getProductId();
        }
        return line.getProduct() != null ? line.getProduct().getId() : null;
    }

    private static Long variantIdOf(OrderProducts line) {
        if (line.getVariantId() != null) {
            return line.getVariantId();
        }
        return line.getVariant() != null ? line.getVariant().getId() : null;
    }
}
