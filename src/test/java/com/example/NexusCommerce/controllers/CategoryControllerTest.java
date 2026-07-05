package com.example.nexusCommerce.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.services.CategoryService;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(CategoryController.class)
public class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void createCategory_returns201() throws Exception {
        // arrange.
        Category testCategory = Category.builder().name("Test Category").build();
        testCategory.setId(1L);
        when(categoryService.createCategory(any())).thenReturn(testCategory);

        // act & assert.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/categories")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\": \"Test Category\"}"))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value("Test Category"));

    }
    // TODO: Add tests for get all categories, get category by id, delete category.
}