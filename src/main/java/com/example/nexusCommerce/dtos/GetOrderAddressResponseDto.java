package com.example.nexusCommerce.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The address an order was shipped to, read back from the order's own snapshot rather than the
 * live address book. Null on orders placed before addresses were captured.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetOrderAddressResponseDto {
    private String recipientName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
