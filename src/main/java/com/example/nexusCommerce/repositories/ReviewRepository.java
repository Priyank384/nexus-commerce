package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductId(Long productId);

    List<Review> findByOrderId(Long orderId);

    boolean existsByOrder_IdAndProduct_Id(Long orderId, Long productId);
}
