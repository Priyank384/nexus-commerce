package com.example.nexusCommerce.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.WishlistAdapter;
import com.example.nexusCommerce.dtos.AddToWishlistRequestDto;
import com.example.nexusCommerce.dtos.CreateWishlistRequestDto;
import com.example.nexusCommerce.dtos.GetWishlistItemResponseDto;
import com.example.nexusCommerce.dtos.GetWishlistResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.repositories.WishlistItemRepository;
import com.example.nexusCommerce.repositories.WishlistRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.schema.Wishlist;
import com.example.nexusCommerce.schema.WishlistItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    public static final String DEFAULT_NAME = "Wishlist";

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final WishlistAdapter wishlistAdapter;

    /**
     * A read never creates a row: the first visit to the heart icon shows an empty list, and the
     * default wishlist is materialised by {@link #addItem} instead.
     */
    @Transactional(readOnly = true)
    @Override
    public GetWishlistResponseDto getMyWishlist(Long userId) {
        return wishlistRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .map(wishlist -> withItems(wishlist, userId))
                .orElseGet(() -> GetWishlistResponseDto.builder()
                        .name(DEFAULT_NAME)
                        .itemCount(0)
                        .items(List.of())
                        .build());
    }

    @Transactional(readOnly = true)
    @Override
    public List<GetWishlistResponseDto> listMyWishlists(Long userId) {
        List<Wishlist> wishlists = wishlistRepository.findByUserId(userId);
        if (wishlists.isEmpty()) {
            return List.of();
        }
        // One grouped count for the whole sidebar; a per-row countByWishlistId is an N+1 on a
        // list the account page reads every time it opens.
        Map<Long, Long> counts = wishlistItemRepository.countGroupedByWishlistIds(
                        wishlists.stream().map(Wishlist::getId).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()));
        return wishlists.stream()
                .map(wishlist -> wishlistAdapter.mapToWishlistSummary(wishlist,
                        counts.getOrDefault(wishlist.getId(), 0L).intValue()))
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public GetWishlistResponseDto create(Long userId, CreateWishlistRequestDto requestDto) {
        String name = requestDto == null || requestDto.getName() == null ? "" : requestDto.getName().trim();
        if (name.isEmpty()) {
            throw new InvalidRequestException("Wishlist name must not be blank!");
        }
        if (wishlistRepository.findByUserIdAndNameIgnoreCase(userId, name).isPresent()) {
            throw new DuplicateResourceException("Wishlist '" + name + "' already exists!");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id: " + userId + " Not Found!"));
        return wishlistAdapter.mapToWishlistSummary(
                wishlistRepository.save(Wishlist.builder().user(user).name(name).build()), 0);
    }

    @Transactional
    @Override
    public void delete(Long userId, Long wishlistId) {
        Wishlist wishlist = requireOwned(userId, wishlistId);
        // Items reference the wishlist with a RESTRICT FK, so they go first or the delete fails.
        wishlistItemRepository.findByWishlistIdOrderByCreatedAtDesc(wishlistId)
                .forEach(wishlistItemRepository::delete);
        wishlistRepository.delete(wishlist);
    }

    @Transactional
    @Override
    public GetWishlistResponseDto addItem(Long userId, Long wishlistId, AddToWishlistRequestDto requestDto) {
        Wishlist wishlist = wishlistId == null
                ? findOrCreateDefault(userId)
                : requireOwned(userId, wishlistId);

        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with id: " + requestDto.getProductId() + " Not Found!"));

        ProductVariant variant = null;
        if (requestDto.getVariantId() != null) {
            variant = variantRepository.findById(requestDto.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant with id: " + requestDto.getVariantId() + " Not Found!"));
            if (!variant.getProductId().equals(product.getId())) {
                throw new InvalidRequestException("Variant " + requestDto.getVariantId()
                        + " does not belong to product " + product.getId());
            }
        }

        WishlistItem existing = wishlistItemRepository
                .findByWishlistIdAndProductId(wishlist.getId(), product.getId())
                .orElse(null);
        if (existing != null) {
            // Re-saving with a different size+colour restates the intent rather than adding a
            // second line: (wishlist, product) is unique among live rows.
            existing.setVariant(variant);
            wishlistItemRepository.save(existing);
        } else {
            wishlistItemRepository.save(WishlistItem.builder()
                    .wishlist(wishlist)
                    .product(product)
                    .variant(variant)
                    .build());
        }

        return withItems(wishlist, userId);
    }

    @Transactional
    @Override
    public void removeItem(Long userId, Long wishlistId, Long productId) {
        // A removal never materialises the default list: unlike addItem, there is nothing to
        // save here, and creating a row just to report that it holds nothing would be wrong.
        Wishlist wishlist = wishlistId == null
                ? wishlistRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product " + productId + " is not saved in a wishlist yet"))
                : requireOwned(userId, wishlistId);
        WishlistItem item = wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product " + productId + " is not in wishlist " + wishlist.getId()));
        wishlistItemRepository.delete(item);
    }

    private Wishlist findOrCreateDefault(Long userId) {
        return wishlistRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "User with id: " + userId + " Not Found!"));
                    return wishlistRepository.save(Wishlist.builder().user(user).name(DEFAULT_NAME).build());
                });
    }

    /** Ownership is checked in one place so every entry point shares the same rule. */
    private Wishlist requireOwned(Long userId, Long wishlistId) {
        Wishlist wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wishlist with id: " + wishlistId + " Not Found!"));
        if (!userId.equals(ownerId(wishlist))) {
            throw new ForbiddenException("Wishlist " + wishlistId + " does not belong to user " + userId);
        }
        return wishlist;
    }

    /**
     * user_id is an insertable=false view of the association, so a wishlist that was just
     * created by addItem has no scalar yet. Reading the identifier off the user reference does
     * not trigger a load either way.
     */
    private static Long ownerId(Wishlist wishlist) {
        if (wishlist.getUserId() != null) {
            return wishlist.getUserId();
        }
        return wishlist.getUser() != null ? wishlist.getUser().getId() : null;
    }

    /**
     * Products and variants come back in two batch queries for the adapter, because open-in-view
     * is off and a per-item lazy load would be an N+1 on a list the storefront re-reads often.
     */
    private GetWishlistResponseDto withItems(Wishlist wishlist, Long userId) {
        if (!userId.equals(ownerId(wishlist))) {
            throw new ForbiddenException("Wishlist " + wishlist.getId() + " does not belong to user " + userId);
        }
        List<WishlistItem> items = wishlistItemRepository.findByWishlistIdOrderByCreatedAtDesc(wishlist.getId());
        List<Long> productIds = items.stream().map(WishlistServiceImpl::productIdOf)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Long> variantIds = items.stream().map(WishlistServiceImpl::variantIdOf)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, ProductVariant> variants = variantIds.isEmpty() ? Map.of()
                : variantRepository.findAllById(variantIds).stream()
                        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<GetWishlistItemResponseDto> mapped = items.stream()
                .map(item -> wishlistAdapter.mapToGetWishlistItemResponseDto(item,
                        products.get(productIdOf(item)),
                        variantIdOf(item) == null ? null : variants.get(variantIdOf(item))))
                .collect(Collectors.toList());

        return wishlistAdapter.mapToGetWishlistResponseDto(wishlist, mapped);
    }

    /**
     * product_id and variant_id are insertable=false views of their associations, so a line saved
     * moments earlier in the same transaction has no scalar populated yet. Reading the identifier
     * off the association instead costs nothing (a proxy answers getId() without loading) and
     * keeps the write response as complete as the next read.
     */
    private static Long productIdOf(WishlistItem item) {
        if (item.getProductId() != null) {
            return item.getProductId();
        }
        return item.getProduct() != null ? item.getProduct().getId() : null;
    }

    private static Long variantIdOf(WishlistItem item) {
        if (item.getVariantId() != null) {
            return item.getVariantId();
        }
        return item.getVariant() != null ? item.getVariant().getId() : null;
    }
}
