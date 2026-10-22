package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.Wishlist;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findByUserId(Long userId);

    Optional<Wishlist> findByUserIdAndNameIgnoreCase(Long userId, String name);

    Optional<Wishlist> findFirstByUserIdOrderByCreatedAtAsc(Long userId);
}
