package com.example.nexusCommerce.dtos;

import java.util.List;

import com.example.nexusCommerce.schema.OrderStatus;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateOrderRequestDto {
    private OrderStatus status;

    // Without @Valid the element constraints (a null action, a non-positive productId) are
    // never checked, so the switch in updateOrder hits a NullPointerException on the first
    // malformed item and returns a 500 instead of a 400.
    @Valid
    private List<OrderItemActionDto> orderItems;
}
