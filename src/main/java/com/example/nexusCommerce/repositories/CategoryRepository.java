package com.example.nexusCommerce.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>{
    
}
