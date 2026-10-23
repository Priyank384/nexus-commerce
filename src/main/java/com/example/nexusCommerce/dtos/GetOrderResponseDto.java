package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetOrderResponseDto {
    
    private Long id;
    private Long userId;
    private OrderStatus orderStatus;
    // Derived from the line-item snapshots, so it can never disagree with the items shown
    // underneath it.
    private BigDecimal totalPrice;
    private PaymentStatus paymentStatus;
    private LocalDateTime paidAt;
    /** Null for orders placed before addresses were captured. */
    private GetOrderAddressResponseDto address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponseDto> items;
}
