package com.example.nexusCommerce.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // The adapter renders the reviewer's display name, so the user is fetched with the page
    // instead of one lazy load per row.
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Review> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductId(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByOrderId(Long orderId, Pageable pageable);

    boolean existsByOrder_IdAndProduct_Id(Long orderId, Long productId);

    long countByProductId(Long productId);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId")
    Double averageRatingByProductId(@Param("productId") Long productId);
}
