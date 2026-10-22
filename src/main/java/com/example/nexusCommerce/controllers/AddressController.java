package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.GetAddressResponseDto;
import com.example.nexusCommerce.dtos.SaveAddressRequestDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.AddressService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Addresses", description = "Saved shipping and billing addresses per authenticated user")
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "List my saved addresses",
               description = "Checkout reads this to prefill the form. Owner-only.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GetAddressResponseDto>>> listMine(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(addressService.listMine(caller.id()), "Addresses fetched"));
    }

    @Operation(summary = "Save a new address",
               description = "The first address a user saves becomes both the shipping and billing default, so "
                           + "checkout works without the shopper having to choose. Owner-only.")
    @PostMapping
    public ResponseEntity<ApiResponse<GetAddressResponseDto>> create(
            @Valid @RequestBody SaveAddressRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                addressService.create(caller.id(), requestDto), "Address saved"));
    }

    @Operation(summary = "Update one of my addresses", description = "Owner-only.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GetAddressResponseDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveAddressRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                addressService.update(caller.id(), id, requestDto), "Address updated"));
    }

    @Operation(summary = "Delete one of my addresses",
               description = "If it was a default, the oldest surviving address takes its place so checkout "
                           + "never ends up with none. Owner-only.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        addressService.delete(caller.id(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted"));
    }

    @Operation(summary = "Make an address the default",
               description = "Pass shipping, billing or both. Exactly one address holds each default, so the "
                           + "previous one is demoted. Owner-only.")
    @PutMapping("/{id}/default")
    public ResponseEntity<ApiResponse<GetAddressResponseDto>> setDefault(
            @PathVariable Long id,
            @RequestParam(name = "shipping", defaultValue = "true") boolean shipping,
            @RequestParam(name = "billing", defaultValue = "false") boolean billing,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                addressService.setDefault(caller.id(), id, shipping, billing), "Default address updated"));
    }
}
