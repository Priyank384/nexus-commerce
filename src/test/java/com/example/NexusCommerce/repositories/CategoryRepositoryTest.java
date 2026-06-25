package com.example.NexusCommerce.repositories;

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

import com.example.NexusCommerce.configs.TestJpaConfig;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.schema.Category;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(TestJpaConfig.class)
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
    void existsByNameIgnoreCase_whenNameExists_returnsTrue() {
        assertTrue(categoryRepository.existsByNameIgnoreCase("electronics"));
    }

    @Test
    void existsByNameIgnoreCase_whenNameDoesNotExist_returnsFalse() {
        assertFalse(categoryRepository.existsByNameIgnoreCase("Books"));
    }

    @Test
    void findByNameIgnoreCase_whenNameExists_returnsCategory() {
        Optional<Category> result = categoryRepository.findByNameIgnoreCase("ELECTRONICS");

        assertTrue(result.isPresent());
        assertEquals("Electronics", result.get().getName());
        assertEquals(electronics.getId(), result.get().getId());
    }

    @Test
    void findByNameIgnoreCase_whenNameDoesNotExist_returnsEmpty() {
        Optional<Category> result = categoryRepository.findByNameIgnoreCase("Books");

        assertTrue(result.isEmpty());
    }
}
