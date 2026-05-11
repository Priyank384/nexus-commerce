package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.nexusCommerce.schema.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetOrderSummaryResponseDto {
    private long id;
    private OrderStatus status;
    private List<OrderItemResponseDto> items;
    private Integer totalItems;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
