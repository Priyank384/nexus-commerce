package com.example.nexusCommerce.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nexusCommerce.schema.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Retires every live line for a product (or a single size+colour) the store is about to
     * stop selling.
     *
     * <p>A cart is live state, not purchase history, so there is nothing to keep: a line whose
     * product or variant no longer exists cannot be priced, cannot pass the stock guard, and
     * makes {@code GET /cart} throw for that shopper until the row is removed by hand. This is
     * the same call V22 made when it deleted unresolvable legacy lines.
     *
     * <p>Written as a bulk UPDATE because a cart can hold lines for many shoppers. The
     * {@code deleted_datetime} guard is spelled out rather than trusted from
     * {@code @SQLRestriction}, which does not apply to bulk statements.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CartItem i SET i.deletedAt = CURRENT_TIMESTAMP "
            + "WHERE i.product.id = :productId AND i.deletedAt IS NULL")
    int retireByProduct(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CartItem i SET i.deletedAt = CURRENT_TIMESTAMP "
            + "WHERE i.variant.id = :variantId AND i.deletedAt IS NULL")
    int retireByVariant(@Param("variantId") Long variantId);
}
