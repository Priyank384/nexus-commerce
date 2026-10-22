package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * V15 replaced the flat unique name with parent-scoped uniqueness, so Men/Denim and
     * Women/Denim may coexist. There is deliberately no single-result findByNameIgnoreCase
     * left: once two live rows can share a name, an Optional-returning lookup throws
     * IncorrectResultSize on the second match.
     */
    boolean existsByNameIgnoreCaseAndParentIdIsNull(String name);

    boolean existsByNameIgnoreCaseAndParentId(String name, Long parentId);

    Optional<Category> findByNameIgnoreCaseAndParentIdIsNull(String name);

    Optional<Category> findByNameIgnoreCaseAndParentId(String name, Long parentId);

    /** For legacy callers that only know a name; deterministic when names repeat. */
    Optional<Category> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    Optional<Category> findBySlugIgnoreCase(String slug);

    /** Top-level rows only -- null parent_id. */
    List<Category> findByParentIdIsNullOrderByCreatedAtAsc();

    List<Category> findByParentIdOrderByCreatedAtAsc(Long parentId);

    long countByParentId(Long parentId);

    /** Category names in use, so a search filter can be rejected before it mints a cache key. */
    @Query("SELECT c.name FROM Category c")
    List<String> findAllNames();
}
