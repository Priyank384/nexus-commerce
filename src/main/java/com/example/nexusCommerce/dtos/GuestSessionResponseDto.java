package com.example.nexusCommerce.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A credential for anonymous shopping. The storefront stores it and replays it in the
 * {@code X-Guest-Token} header; it is the only proof a guest cart and a guest order have, so it
 * belongs in the browser and nowhere else.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestSessionResponseDto {

    private String guestToken;
}
