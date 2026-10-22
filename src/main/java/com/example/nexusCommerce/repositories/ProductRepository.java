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

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT DISTINCT c.name FROM products p "
            + "JOIN categories c ON p.category_id = c.id "
            + "WHERE p.deleted_datetime IS NULL AND c.deleted_datetime IS NULL", nativeQuery = true)
    List<String> findUniqueCategory();

    @Query("select p from Product p JOIN FETCH p.category WHERE p.id = :id")
    List<Product> findProductWithDetailsById(Long id);

    long countByStockLessThanEqual(Integer stock);

    /**
     * Atomic, conditional decrement so two concurrent orders can never oversell the last
     * unit: the row is only updated while enough stock remains, and the affected-row count
     * tells the caller whether it won the race.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomic stock return. Done as a bulk UPDATE rather than a read-modify-write on a
     * managed entity: two concurrent returns for the same product would otherwise both read
     * the old stock and the second write would silently clobber the first (lost update).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
