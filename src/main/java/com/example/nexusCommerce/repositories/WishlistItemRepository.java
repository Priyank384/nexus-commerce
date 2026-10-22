package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nexusCommerce.schema.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByWishlistIdOrderByCreatedAtDesc(Long wishlistId);

    long countByWishlistId(Long wishlistId);

    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);

    /**
     * Retires saved items for a style the store stops selling, for the same reason cart lines
     * are retired: a saved row that no longer resolves to a product renders as a blank card and
     * cannot be bought, so it is a broken promise rather than a wish worth keeping.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WishlistItem i SET i.deletedAt = CURRENT_TIMESTAMP "
            + "WHERE i.product.id = :productId AND i.deletedAt IS NULL")
    int retireByProduct(@Param("productId") Long productId);
}
