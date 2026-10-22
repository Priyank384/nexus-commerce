package com.example.nexusCommerce.services;

import java.util.List;

import com.example.nexusCommerce.dtos.GetAddressResponseDto;
import com.example.nexusCommerce.dtos.SaveAddressRequestDto;

public interface AddressService {
    List<GetAddressResponseDto> listMine(Long userId);
    GetAddressResponseDto create(Long userId, SaveAddressRequestDto requestDto);
    GetAddressResponseDto update(Long userId, Long addressId, SaveAddressRequestDto requestDto);
    void delete(Long userId, Long addressId);
    GetAddressResponseDto setDefault(Long userId, Long addressId, boolean shipping, boolean billing);
}
