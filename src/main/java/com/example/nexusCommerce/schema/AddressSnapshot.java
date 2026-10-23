package com.example.nexusCommerce.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What an order shipped to, frozen at the moment the order was placed. It is stored as JSON on
 * the order so later edits or deletions in the address book cannot rewrite where an old order went.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {

    private String recipientName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    public static AddressSnapshot from(Address address) {
        return AddressSnapshot.builder()
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }
}
