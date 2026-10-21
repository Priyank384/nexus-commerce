package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SetStockRequestDto {

    @NotNull(message = "stock must not be null")
    @PositiveOrZero(message = "stock must not be negative")
    private Integer stock;
}
