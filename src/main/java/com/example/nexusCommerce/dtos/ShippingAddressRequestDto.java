package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A shipping address typed at checkout instead of picked from the address book. Guests have no
 * address book, and a signed-in shopper may want to send a gift somewhere they have never
 * shipped before.
 *
 * <p>Same field rules as {@link SaveAddressRequestDto}, deliberately without its label and
 * default flags: this is a destination for one order, not a saved entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressRequestDto {

    @NotBlank(message = "Recipient name must not be blank")
    @Size(max = 120, message = "Recipient name must be at most 120 characters")
    private String recipientName;

    @NotBlank(message = "Phone must not be blank")
    @Pattern(regexp = "^[0-9+\\-\\s()]{6,20}$", message = "Phone must be a valid number")
    private String phone;

    @NotBlank(message = "Address line 1 must not be blank")
    @Size(max = 255, message = "Address line 1 must be at most 255 characters")
    private String line1;

    @Size(max = 255, message = "Address line 2 must be at most 255 characters")
    private String line2;

    @NotBlank(message = "City must not be blank")
    @Size(max = 80, message = "City must be at most 80 characters")
    private String city;

    @NotBlank(message = "State must not be blank")
    @Size(max = 80, message = "State must be at most 80 characters")
    private String state;

    @NotBlank(message = "Postal code must not be blank")
    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]{3,20}$", message = "Postal code is not valid")
    private String postalCode;

    @Size(min = 2, max = 2, message = "Country must be a 2-letter code")
    private String country;
}
