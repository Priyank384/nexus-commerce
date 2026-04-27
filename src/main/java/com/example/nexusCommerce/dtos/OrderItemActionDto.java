package com.example.nexusCommerce.dtos;

import com.example.nexusCommerce.schema.OrderItemAction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItemActionDto {
    private long productId;
    private Integer quantity;
    private OrderItemAction action;
}
