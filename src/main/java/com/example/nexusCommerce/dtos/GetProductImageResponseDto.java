package com.example.nexusCommerce.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetProductImageResponseDto {
    private Long id;
    private String url;
    /** 0 is the primary/thumbnail image. */
    private Integer position;
    private String alt;
}
