package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductImageRequestDto {

    @NotBlank(message = "Image url must not be blank")
    private String url;

    @PositiveOrZero(message = "Position must not be negative")
    @Builder.Default
    private Integer position = 0;

    @Size(max = 255, message = "Alt text must be at most 255 characters")
    private String alt;
}
