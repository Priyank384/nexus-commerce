package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Attaches a guest order to the account making the call. The token is the one mailed inside the
 * confirmation link, which is what lets an order bought on one device be claimed on another.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimOrderRequestDto {

    @NotBlank(message = "A claim token is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "That is not a claim token")
    private String claimToken;
}
