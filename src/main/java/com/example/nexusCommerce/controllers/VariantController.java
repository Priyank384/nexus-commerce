package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateVariantRequestDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.dtos.UpdateVariantRequestDto;
import com.example.nexusCommerce.services.VariantService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Writes live here rather than on the product payload: order lines reference variant ids, so
 * replacing a whole variant set through a product PUT would silently orphan purchases.
 * Reads stay on {@code ProductController}, where they are cached with the product.
 */
@Tag(name = "Variants", description = "Size and colour stock for a product (admin writes)")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class VariantController {

    private final VariantService variantService;

    @Operation(summary = "Add a variant to a product",
               description = "Creates one size+colour. The SKU is stored upper-cased and must be unique, and the "
                           + "size+colour pair must not already exist on the product. Stock set here is what the "
                           + "variant is sold with; the product's aggregate follows. Requires ROLE_ADMIN.")
    @PostMapping("/{productId}/variants")
    public ResponseEntity<ApiResponse<GetVariantResponseDto>> create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateVariantRequestDto requestDto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(variantService.create(productId, requestDto), "Variant created successfully!"));
    }

    @Operation(summary = "Update a variant",
               description = "Patch-style: only the fields present in the body change. Requires ROLE_ADMIN.")
    @PutMapping("/variants/{variantId}")
    public ResponseEntity<ApiResponse<GetVariantResponseDto>> update(
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequestDto requestDto){
        return ResponseEntity.ok(ApiResponse.success(variantService.update(variantId, requestDto), "Variant updated successfully!"));
    }

    @Operation(summary = "Delete a variant",
               description = "Soft-deletes the variant. Refused for a product's last variant -- delete the product "
                           + "instead of leaving it unpurchasable. Requires ROLE_ADMIN.")
    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long variantId){
        variantService.delete(variantId);
        return ResponseEntity.ok(ApiResponse.success(null, "Variant deleted successfully!"));
    }
}
