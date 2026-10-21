package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductId(Long productId, Pageable pageable);

    Page<Review> findByOrderId(Long orderId, Pageable pageable);


    boolean existsByOrder_IdAndProduct_Id(Long orderId, Long productId);
}
