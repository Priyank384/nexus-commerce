package com.example.nexusCommerce.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.AddressAdapter;
import com.example.nexusCommerce.dtos.GetAddressResponseDto;
import com.example.nexusCommerce.dtos.SaveAddressRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.AddressRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Address;
import com.example.nexusCommerce.schema.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private static final int MAX_PER_USER = 20;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressAdapter addressAdapter;

    @Transactional(readOnly = true)
    @Override
    public List<GetAddressResponseDto> listMine(Long userId) {
        return addressAdapter.mapToGetAddressResponseDtoList(
                addressRepository.findByUserIdOrderByCreatedAtAsc(userId));
    }

    @Transactional
    @Override
    public GetAddressResponseDto create(Long userId, SaveAddressRequestDto requestDto) {
        List<Address> existing = addressRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (existing.size() >= MAX_PER_USER) {
            throw new InvalidRequestException("An account can hold at most " + MAX_PER_USER + " addresses");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id: " + userId + " Not Found!"));

        Address address = Address.builder()
                .user(user)
                .label(fallback(trimToNull(requestDto.getLabel()), "Home"))
                .recipientName(requestDto.getRecipientName().trim())
                .phone(requestDto.getPhone().trim())
                .line1(requestDto.getLine1().trim())
                .line2(trimToNull(requestDto.getLine2()))
                .city(requestDto.getCity().trim())
                .state(requestDto.getState().trim())
                .postalCode(requestDto.getPostalCode().trim())
                .country(fallback(trimToNull(requestDto.getCountry()), "IN").toUpperCase(java.util.Locale.ROOT))
                .defaultShipping(Boolean.TRUE.equals(requestDto.getDefaultShipping()))
                .defaultBilling(Boolean.TRUE.equals(requestDto.getDefaultBilling()))
                .build();

        // The first address is the only candidate for the defaults, so checkout can always
        // find one without the storefront having to press a "make default" button first.
        boolean first = existing.isEmpty();
        if (first) {
            address.setDefaultShipping(true);
            address.setDefaultBilling(true);
        }

        Address saved = addressRepository.save(address);
        enforceSingleDefault(saved, user.getId(), saved.getDefaultShipping(), saved.getDefaultBilling());
        return addressAdapter.mapToGetAddressResponseDto(saved);
    }

    @Transactional
    @Override
    public GetAddressResponseDto update(Long userId, Long addressId, SaveAddressRequestDto requestDto) {
        Address address = requireOwned(userId, addressId);
        address.setLabel(fallback(trimToNull(requestDto.getLabel()), address.getLabel()));
        address.setRecipientName(requestDto.getRecipientName().trim());
        address.setPhone(requestDto.getPhone().trim());
        address.setLine1(requestDto.getLine1().trim());
        address.setLine2(trimToNull(requestDto.getLine2()));
        address.setCity(requestDto.getCity().trim());
        address.setState(requestDto.getState().trim());
        address.setPostalCode(requestDto.getPostalCode().trim());
        address.setCountry(fallback(trimToNull(requestDto.getCountry()), address.getCountry())
                .toUpperCase(java.util.Locale.ROOT));
        boolean shipping = requestDto.getDefaultShipping() != null
                ? requestDto.getDefaultShipping() : address.getDefaultShipping();
        boolean billing = requestDto.getDefaultBilling() != null
                ? requestDto.getDefaultBilling() : address.getDefaultBilling();

        Address saved = addressRepository.save(address);
        enforceSingleDefault(saved, userId, shipping, billing);
        return addressAdapter.mapToGetAddressResponseDto(saved);
    }

    @Transactional
    @Override
    public void delete(Long userId, Long addressId) {
        Address address = requireOwned(userId, addressId);
        addressRepository.delete(address);
        if (address.getDefaultShipping() || address.getDefaultBilling()) {
            promoteOldest(userId, address.getDefaultShipping(), address.getDefaultBilling());
        }
    }

    @Transactional
    @Override
    public GetAddressResponseDto setDefault(Long userId, Long addressId, boolean shipping, boolean billing) {
        if (!shipping && !billing) {
            throw new InvalidRequestException("At least one of shipping or billing must be true");
        }
        Address address = requireOwned(userId, addressId);
        enforceSingleDefault(address, userId, shipping, billing);
        return addressAdapter.mapToGetAddressResponseDto(address);
    }

    /**
     * "Default" is a property of the user, not of a row: two defaults would make checkout pick
     * one arbitrarily, so promoting one address demotes every other address of that user.
     */
    private void enforceSingleDefault(Address winner, Long userId, boolean shipping, boolean billing) {
        winner.setDefaultShipping(shipping);
        winner.setDefaultBilling(billing);
        addressRepository.save(winner);

        for (Address other : addressRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (other.getId().equals(winner.getId())) {
                continue;
            }
            boolean changed = false;
            if (shipping && Boolean.TRUE.equals(other.getDefaultShipping())) {
                other.setDefaultShipping(false);
                changed = true;
            }
            if (billing && Boolean.TRUE.equals(other.getDefaultBilling())) {
                other.setDefaultBilling(false);
                changed = true;
            }
            if (changed) {
                addressRepository.save(other);
            }
        }

        // Refusing to leave a user with no default is what keeps checkout from failing on a
        // request that only asked to unset one.
        if (shipping && addressRepository.findByUserIdAndDefaultShippingTrue(userId).isEmpty()) {
            promoteOldest(userId, true, false);
        }
        if (billing && addressRepository.findByUserIdAndDefaultBillingTrue(userId).isEmpty()) {
            promoteOldest(userId, false, true);
        }
    }

    private void promoteOldest(Long userId, boolean shipping, boolean billing) {
        List<Address> remaining = addressRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (remaining.isEmpty()) {
            return;
        }
        Address next = remaining.get(0);
        if (shipping) {
            next.setDefaultShipping(true);
        }
        if (billing) {
            next.setDefaultBilling(true);
        }
        addressRepository.save(next);
    }

    private Address requireOwned(Long userId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address with id: " + addressId + " Not Found!"));
        if (!address.getUserId().equals(userId)) {
            throw new ForbiddenException("Address " + addressId + " does not belong to user " + userId);
        }
        return address;
    }

    private static String fallback(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
