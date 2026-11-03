package com.example.nexusCommerce.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateOrderRequestDto {

    @NotEmpty(message = "An order must contain at least one item")
    @Valid
    private List<OrderItemRequestDto> orderItems;

    /**
     * The saved address to ship to. The cart checkout requires it for an account; a guest
     * sends {@link #shipping} instead, and neither is required for the direct order endpoint so
     * historic and admin-created orders keep working.
     */
    @Positive
    private Long addressId;

    /** Where the confirmation mail goes when the buyer has no account. Ignored for a signed-in caller. */
    @Email(message = "Checkout email is not a valid address")
    @Size(max = 255, message = "Checkout email must be at most 255 characters")
    private String email;

    /**
     * A destination typed at checkout rather than picked from the address book, which is the
     * only option a guest has. Takes precedence over {@link #addressId}.
     */
    @Valid
    private ShippingAddressRequestDto shipping;
}
