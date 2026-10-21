package com.example.nexusCommerce.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
}
