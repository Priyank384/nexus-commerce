package com.example.nexusCommerce.dtos;

import java.util.List;

import com.example.nexusCommerce.schema.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class updateOrderRequestDto {
    private OrderStatus status;
    private List<OrderItemActionDto> orderItems;
}
