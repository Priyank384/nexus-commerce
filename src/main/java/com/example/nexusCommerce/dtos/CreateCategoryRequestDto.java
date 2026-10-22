package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequestDto {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 120, message = "Category name must be at most 120 characters")
    private String name;

    /** Null creates a top-level category. */
    @Positive(message = "parentId must be positive")
    private Long parentId;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    /** Optional: derived from the name by the service when absent. */
    @Size(max = 80, message = "Slug must be at most 80 characters")
    private String slug;
}
