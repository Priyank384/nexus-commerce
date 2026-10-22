package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByWishlistIdOrderByCreatedAtDesc(Long wishlistId);

    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
}
