package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductImage> findTopByProductIdOrderByPositionDesc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
