package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);
}
