package com.example.nexusCommerce.dtos;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReorderProductImagesRequestDto {

    /** Every live gallery image of the product, in the new display order. */
    @NotEmpty(message = "imageIds must list every gallery image in order")
    private List<@NotNull @Positive Long> imageIds;
}
