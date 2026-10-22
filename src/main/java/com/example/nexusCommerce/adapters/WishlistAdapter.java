package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetWishlistItemResponseDto;
import com.example.nexusCommerce.dtos.GetWishlistResponseDto;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.Wishlist;
import com.example.nexusCommerce.schema.WishlistItem;

@Component
public class WishlistAdapter {

    /** For list endpoints, where loading every item's product would be an N+1. */
    public GetWishlistResponseDto mapToWishlistSummary(Wishlist wishlist, int itemCount) {
        return GetWishlistResponseDto.builder()
                .id(wishlist.getId())
                .name(wishlist.getName())
                .itemCount(itemCount)
                .build();
    }

    public GetWishlistResponseDto mapToGetWishlistResponseDto(Wishlist wishlist,
                                                             List<GetWishlistItemResponseDto> items) {
        return GetWishlistResponseDto.builder()
                .id(wishlist.getId())
                .name(wishlist.getName())
                .itemCount(items.size())
                .items(items)
                .build();
    }

    /**
     * Pure mapping: open-in-view is off, so {@code item.getProduct()} and
     * {@code item.getVariant()} are unresolvable outside the service transaction. The caller
     * loads them and hands them over.
     */
    public GetWishlistItemResponseDto mapToGetWishlistItemResponseDto(WishlistItem item,
                                                                     Product product,
                                                                     ProductVariant variant) {
        GetWishlistItemResponseDto.GetWishlistItemResponseDtoBuilder builder =
                GetWishlistItemResponseDto.builder()
                        .id(item.getId())
                        // Prefer the loaded product's id: item.productId is an insertable=false
                        // view of the association and is only backfilled on a loaded row.
                        .productId(product != null ? product.getId() : item.getProductId())
                        .productTitle(product != null ? product.getTitle() : null)
                        .productImage(product != null ? product.getImage() : null)
                        .price(product != null ? product.getPrice() : null)
                        .addedAt(item.getCreatedAt());

        if (variant != null) {
            builder.variantId(variant.getId())
                    .sku(variant.getSku())
                    .size(variant.getSize())
                    .colorName(variant.getColorName())
                    .colorHex(variant.getColorHex());
        }
        return builder.build();
    }
}
