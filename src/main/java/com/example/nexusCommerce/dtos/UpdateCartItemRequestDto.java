package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCartItemRequestDto {
    @NotNull
    @Min(1)
    @Max(value = 99, message = "A cart line cannot hold more than 99 units")
    private Integer quantity;
}
