package com.example.nexusCommerce.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.security.JwtAuthFilter;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private GetCategoryResponseDto category(Long id, String name) {
        return GetCategoryResponseDto.builder().id(id).name(name).build();
    }

    @Test
    void createCategory_returns201() throws Exception {
        when(categoryService.createCategory(any())).thenReturn(category(1L, "Test Category"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/categories")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\": \"Test Category\"}"))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value("Test Category"));
    }

    @Test
    void getAllCategory_returnsPageEnvelope() throws Exception {
        PageResponse<GetCategoryResponseDto> page = PageResponse.of(
                new PageImpl<>(List.of(category(1L, "Electronics")), PageRequest.of(0, 20), 102),
                c -> c);
        when(categoryService.getAllCategory(0, 20)).thenReturn(page);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.content[0].name").value("Electronics"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.totalElements").value(102))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.content[0].deletedAt").doesNotExist());
    }

    @Test
    void getAllCategory_clampsOversizedRequestAtTheBoundary() throws Exception {
        when(categoryService.getAllCategory(eq(0), eq(Pagination.MAX_SIZE))).thenReturn(
                PageResponse.of(new PageImpl<>(List.<GetCategoryResponseDto>of(), PageRequest.of(0, Pagination.MAX_SIZE), 0), c -> c));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories").param("size", "100000"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // The cache key is built from the size the service receives, so the clamped value
        // is what keeps one entry per page instead of one per arbitrary request number.
        verify(categoryService).getAllCategory(0, Pagination.MAX_SIZE);
    }

    @Test
    void getCategoryById_returns200() throws Exception {
        when(categoryService.getCategoryById(7L)).thenReturn(category(7L, "Books"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/7"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.id").value(7));
    }

    @Test
    void deleteCategory_returns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/7"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        verify(categoryService).deleteCategory(7L);
    }
}
