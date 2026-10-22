package com.example.nexusCommerce.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.adapters.AddressAdapter;
import com.example.nexusCommerce.dtos.SaveAddressRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.AddressRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Address;

/**
 * Address book rows are the most direct PII in the API, so "not your address" has to stay a
 * 403 (see ForbiddenException) rather than the 400 it used to answer.
 */
@ExtendWith(MockitoExtension.class)
public class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressAdapter addressAdapter;

    @InjectMocks
    private AddressServiceImpl service;

    private Address theirs(Long addressId, Long ownerId) {
        Address address = Address.builder()
                .recipientName("Ravi").phone("9999999999").line1("1 MG Road")
                .city("Bengaluru").state("KA").postalCode("560001").country("IN")
                .defaultShipping(false).defaultBilling(false).build();
        address.setId(addressId);
        address.setUserId(ownerId);
        return address;
    }

    private SaveAddressRequestDto request() {
        SaveAddressRequestDto dto = new SaveAddressRequestDto();
        dto.setRecipientName("Ravi");
        dto.setPhone("9999999999");
        dto.setLine1("1 MG Road");
        dto.setCity("Bengaluru");
        dto.setState("KA");
        dto.setPostalCode("560001");
        return dto;
    }

    @Test
    void update_ofAnotherUsersAddress_isForbiddenAndUntouched() {
        when(addressRepository.findById(4L)).thenReturn(Optional.of(theirs(4L, 8L)));

        assertThrows(ForbiddenException.class, () -> service.update(7L, 4L, request()));
        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void delete_ofAnotherUsersAddress_isForbiddenAndKept() {
        when(addressRepository.findById(4L)).thenReturn(Optional.of(theirs(4L, 8L)));

        assertThrows(ForbiddenException.class, () -> service.delete(7L, 4L));
        verify(addressRepository, never()).delete(any(Address.class));
    }

    @Test
    void setDefault_ofAnotherUsersAddress_isForbidden() {
        when(addressRepository.findById(4L)).thenReturn(Optional.of(theirs(4L, 8L)));

        assertThrows(ForbiddenException.class, () -> service.setDefault(7L, 4L, true, false));
    }

    @Test
    void setDefault_withNeitherFlag_isAnInvalidRequestNotADenial() {
        assertThrows(InvalidRequestException.class, () -> service.setDefault(7L, 4L, false, false));
        verify(addressRepository, never()).findById(4L);
    }

    @Test
    void update_ofMissingAddress_isNotFound() {
        when(addressRepository.findById(4L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(7L, 4L, request()));
    }

    @Test
    void update_ofOwnAddress_succeeds() {
        Address mine = theirs(4L, 7L);
        when(addressRepository.findById(4L)).thenReturn(Optional.of(mine));
        when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
        when(addressRepository.findByUserIdOrderByCreatedAtAsc(7L)).thenReturn(java.util.List.of());

        service.update(7L, 4L, request());

        verify(addressRepository, atLeastOnce()).save(any(Address.class));
        verify(addressAdapter).mapToGetAddressResponseDto(mine);
    }
}
