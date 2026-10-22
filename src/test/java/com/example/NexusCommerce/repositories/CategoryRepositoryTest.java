package com.example.nexusCommerce.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.example.nexusCommerce.configs.JpaAuditingConfig;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.schema.Category;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
class CategoryRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category electronics;

    @BeforeEach
    void setUp() {
        electronics = Category.builder().name("Electronics").build();
        testEntityManager.persistAndFlush(electronics);
        testEntityManager.clear();
    }

    @Test
    void existsByNameIgnoreCaseAndParentIdIsNull_whenRootNameExists_returnsTrue() {
        assertTrue(categoryRepository.existsByNameIgnoreCaseAndParentIdIsNull("electronics"));
    }

    @Test
    void existsByNameIgnoreCaseAndParentIdIsNull_whenNameDoesNotExist_returnsFalse() {
        assertFalse(categoryRepository.existsByNameIgnoreCaseAndParentIdIsNull("Books"));
    }

    @Test
    void findByNameIgnoreCaseAndParentIdIsNull_whenNameExists_returnsCategory() {
        Optional<Category> result = categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("ELECTRONICS");

        assertTrue(result.isPresent());
        assertEquals("Electronics", result.get().getName());
        assertEquals(electronics.getId(), result.get().getId());
    }

    @Test
    void findByNameIgnoreCaseAndParentIdIsNull_whenNameDoesNotExist_returnsEmpty() {
        assertTrue(categoryRepository.findByNameIgnoreCaseAndParentIdIsNull("Books").isEmpty());
    }

    /**
     * V15 replaced the globally unique name with parent-scoped uniqueness, so Men/Denim and
     * Women/Denim legitimately coexist. These two assertions are what make that legal rather
     * than merely tolerated.
     */
    @Test
    void sameNameUnderDifferentParents_isAllowedAndResolvesPerParent() {
        Category men = testEntityManager.persistAndFlush(Category.builder().name("Men").build());
        Category women = testEntityManager.persistAndFlush(Category.builder().name("Women").build());
        Category menDenim = Category.builder().name("Denim").parent(men).build();
        Category womenDenim = Category.builder().name("Denim").parent(women).build();
        testEntityManager.persistAndFlush(menDenim);
        testEntityManager.persistAndFlush(womenDenim);
        testEntityManager.clear();

        assertTrue(categoryRepository.findByNameIgnoreCaseAndParentId("denim", men.getId()).isPresent());
        assertEquals(menDenim.getId(),
                categoryRepository.findByNameIgnoreCaseAndParentId("DENIM", men.getId()).orElseThrow().getId());
        assertEquals(womenDenim.getId(),
                categoryRepository.findByNameIgnoreCaseAndParentId("Denim", women.getId()).orElseThrow().getId());
        assertFalse(categoryRepository.existsByNameIgnoreCaseAndParentIdIsNull("Denim"));
    }

    @Test
    void findFirstByNameIgnoreCaseOrderByIdAsc_picksTheOldestMatchWhenNamesCollideAcrossParents() {
        Category men = testEntityManager.persistAndFlush(Category.builder().name("Men").build());
        Category mensDenim = testEntityManager.persistAndFlush(Category.builder().name("Denim").parent(men).build());
        Category women = testEntityManager.persistAndFlush(Category.builder().name("Women").build());
        testEntityManager.persistAndFlush(Category.builder().name("Denim").parent(women).build());
        testEntityManager.clear();

        Optional<Category> first = categoryRepository.findFirstByNameIgnoreCaseOrderByIdAsc("denim");

        assertTrue(first.isPresent());
        assertEquals(mensDenim.getId(), first.get().getId());
    }
}
