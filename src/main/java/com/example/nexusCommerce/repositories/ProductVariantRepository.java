package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nexusCommerce.schema.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderBySizeAscColorNameAsc(Long productId);

    List<ProductVariant> findByProductIdInOrderByProductIdAscSizeAscColorNameAsc(List<Long> productIds);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * Exactly one variant for the product. Used to resolve a variant-less cart or order line
     * against a legacy product, and deliberately not a list: if an admin ever added a second
     * size to such a product the ambiguous read would have to fail rather than pick one.
     */
    Optional<ProductVariant> findFirstByProductIdOrderByCreatedAtAsc(Long productId);

    long countByProductId(Long productId);

    /**
     * Same conditional-decrement contract as {@code ProductRepository.decrementStock}: the
     * variant row only moves while stock remains, so concurrent buyers cannot oversell one
     * size+colour.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = v.stock - :qty "
            + "WHERE v.id = :id AND v.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = v.stock + :qty WHERE v.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * The variants are now the source of truth for stock; {@code products.stock} is kept as a
     * legacy aggregate so old readers and the admin low-stock report stay honest. Summing here
     * and writing one value beats a correlated subquery in an UPDATE, which HQL cannot express
     * portably across MySQL and PostgreSQL.
     */
    @Query("SELECT COALESCE(SUM(v.stock), 0) FROM ProductVariant v WHERE v.productId = :productId")
    long sumStockByProductId(@Param("productId") Long productId);
}
