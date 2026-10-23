package com.example.nexusCommerce.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateOrderRequestDto {

    @NotEmpty(message = "An order must contain at least one item")
    @Valid
    private List<OrderItemRequestDto> orderItems;

    /**
     * The saved address to ship to. The cart checkout requires it; the direct order endpoint
     * leaves it optional so historic/admin-created orders keep working.
     */
    @Positive
    private Long addressId;
}
