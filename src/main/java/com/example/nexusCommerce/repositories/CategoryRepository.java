package com.example.nexusCommerce.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>{
    boolean existsByNameIgnoreCase(String name);

    Optional<Category> findByNameIgnoreCase(String name);
}
