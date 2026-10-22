package com.example.nexusCommerce.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // items.variant is fetched with the rest: since V22 every line is variant-scoped and the
    // read model needs its size, colour and stock. open-in-view is off, so a lazy association
    // here would surface as a LazyInitializationException in the response mapper.
    @EntityGraph(attributePaths = {"items", "items.product", "items.variant"})
    Optional<Cart> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.variant"})
    Optional<Cart> findById(Long id);
}
