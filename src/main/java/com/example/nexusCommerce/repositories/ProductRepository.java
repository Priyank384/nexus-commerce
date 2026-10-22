package com.example.nexusCommerce.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>{

    // Fetch-join the category so the read model can map product+category without a
    // per-row lazy load (1 + N queries). Safe with pagination because the association is to-one.
    @EntityGraph(attributePaths = "category")
    Page<Product> findByCategory_NameIgnoreCase(String categoryName, Pageable pageable);

    boolean existsByTitleIgnoreCase(String name);

    boolean existsByCategory_Id(Long categoryId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query(value = "SELECT DISTINCT c.name FROM products p "
            + "JOIN categories c ON p.category_id = c.id "
            + "WHERE p.deleted_datetime IS NULL AND c.deleted_datetime IS NULL", nativeQuery = true)
    List<String> findUniqueCategory();

    @Query("select p from Product p JOIN FETCH p.category WHERE p.id = :id")
    List<Product> findProductWithDetailsById(Long id);

    /**
     * Faceted catalogue search. Size and colour filters use EXISTS rather than a join so a
     * product with six matching variants still counts once, which keeps both the page and
     * the total correct without DISTINCT. The {@code *Count} parameters exist because a list
     * cannot be compared to NULL in JPQL: zero means "no filter", and the list always holds
     * at least one element so no engine has to render an empty IN ().
     */
    @Query(value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            LEFT JOIN c.parent pc
            WHERE p.isActive = TRUE
              AND (:gender IS NULL OR LOWER(p.gender) = :gender)
              AND (:categorySlug IS NULL OR LOWER(c.slug) = :categorySlug OR LOWER(pc.slug) = :categorySlug)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:sizeCount = 0 OR EXISTS (
                    SELECT 1 FROM ProductVariant v WHERE v.product = p AND LOWER(v.size) IN :sizes))
              AND (:colorCount = 0 OR EXISTS (
                    SELECT 1 FROM ProductVariant v WHERE v.product = p AND LOWER(v.colorName) IN :colors))
            """,
        countQuery = """
            SELECT COUNT(p) FROM Product p
            JOIN p.category c
            LEFT JOIN c.parent pc
            WHERE p.isActive = TRUE
              AND (:gender IS NULL OR LOWER(p.gender) = :gender)
              AND (:categorySlug IS NULL OR LOWER(c.slug) = :categorySlug OR LOWER(pc.slug) = :categorySlug)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:sizeCount = 0 OR EXISTS (
                    SELECT 1 FROM ProductVariant v WHERE v.product = p AND LOWER(v.size) IN :sizes))
              AND (:colorCount = 0 OR EXISTS (
                    SELECT 1 FROM ProductVariant v WHERE v.product = p AND LOWER(v.colorName) IN :colors))
            """)
    Page<Product> search(@Param("gender") String gender,
                         @Param("categorySlug") String categorySlug,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("sizes") List<String> sizes,
                         @Param("sizeCount") int sizeCount,
                         @Param("colors") List<String> colors,
                         @Param("colorCount") int colorCount,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"category", "category.parent"})
    Optional<Product> findBySlugIgnoreCase(String slug);

    /**
     * Every size and colour a shopper can actually buy, used both to publish the facets and to
     * reject invented filter values. Joined through the product deliberately: a variant under an
     * unpublished or deleted product must not advertise a size the storefront will not sell,
     * because that answers a valid-looking filter with an empty page.
     */
    @Query("SELECT DISTINCT v.size FROM ProductVariant v JOIN v.product p WHERE p.isActive = TRUE")
    List<String> findUniqueVariantSizes();

    @Query("SELECT DISTINCT v.colorName FROM ProductVariant v JOIN v.product p WHERE p.isActive = TRUE")
    List<String> findUniqueVariantColors();

    long countByStockLessThanEqual(Integer stock);

    /**
     * Atomic stock return. Done as a bulk UPDATE rather than a read-modify-write on a
     * managed entity: two concurrent returns for the same product would otherwise both read
     * the old stock and the second write would silently clobber the first (lost update).
     * Only pre-V20 order lines, which carry no variant, are refunded this way; every other
     * reservation returns stock on its variant.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** Absolute write used to mirror the variant stock aggregate onto the legacy column. */
    @Modifying
    @Query("UPDATE Product p SET p.stock = :stock WHERE p.id = :id")
    int setStock(@Param("id") Long id, @Param("stock") int stock);
}
