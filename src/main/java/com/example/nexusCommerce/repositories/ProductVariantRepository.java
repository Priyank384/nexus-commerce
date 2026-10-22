package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderBySizeAscColorNameAsc(Long productId);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);
}
