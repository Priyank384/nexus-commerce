package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>{
    List<Product> findByCategory_NameIgnoreCase(String categoryName);

    boolean existsByTitleIgnoreCase(String name);

    @Query(value = "SELECT DISTINCT c.name FROM products p JOIN categories c ON p.category_id = c.id", nativeQuery = true)
    List<String> findUniqueCategory();

    @Query("select p from Product p JOIN FETCH p.category WHERE p.id = :id")
    List<Product> findProductWithDetailsById(Long id);
}
