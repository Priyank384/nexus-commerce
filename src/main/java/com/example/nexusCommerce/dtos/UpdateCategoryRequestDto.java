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
public class UpdateCategoryRequestDto {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 120, message = "Category name must be at most 120 characters")
    private String name;

    /** Null keeps the current parent; send moveToTopLevel to detach the category. */
    @Positive(message = "parentId must be positive")
    private Long parentId;

    /**
     * A missing JSON field and an explicit null both bind to null on parentId, so detaching a
     * category from its parent needs its own flag.
     */
    private Boolean moveToTopLevel;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    /** Blank keeps the current slug. */
    @Size(max = 80, message = "Slug must be at most 80 characters")
    private String slug;
}
