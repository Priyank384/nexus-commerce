package com.example.nexusCommerce.dtos;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetWishlistResponseDto {
    private Long id;
    private String name;
    private Integer itemCount;

    /** Only populated by GET /wishlist/{id}; list endpoints return the summary. */
    @Builder.Default
    private List<GetWishlistItemResponseDto> items = new ArrayList<>();
}
