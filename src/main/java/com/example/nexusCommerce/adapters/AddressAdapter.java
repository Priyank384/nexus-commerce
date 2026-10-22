package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetAddressResponseDto;
import com.example.nexusCommerce.schema.Address;

@Component
public class AddressAdapter {

    public GetAddressResponseDto mapToGetAddressResponseDto(Address address) {
        return GetAddressResponseDto.builder()
                .id(address.getId())
                .label(address.getLabel())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .defaultShipping(address.getDefaultShipping())
                .defaultBilling(address.getDefaultBilling())
                .createdAt(address.getCreatedAt())
                .build();
    }

    public List<GetAddressResponseDto> mapToGetAddressResponseDtoList(List<Address> addresses) {
        return addresses.stream().map(this::mapToGetAddressResponseDto).collect(Collectors.toList());
    }
}
