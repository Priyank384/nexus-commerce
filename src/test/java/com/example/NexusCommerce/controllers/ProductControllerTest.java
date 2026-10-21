package com.example.nexusCommerce.controllers;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.security.JwtAuthFilter;
import com.example.nexusCommerce.services.ProductService;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void createProduct_valid_returns201() throws Exception {
        when(productService.createProduct(any()))
                .thenReturn(GetProductResponseDto.builder().id(1L).title("iPhone").price(BigDecimal.TEN).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"iPhone\",\"price\":10,\"rating\":4.5,\"categoryId\":1}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.title").value("iPhone"));
    }

    @Test
    void createProduct_blankTitle_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \",\"price\":10,\"rating\":4.5,\"categoryId\":1}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        verify(productService, never()).createProduct(any(CreateProductRequestDto.class));
    }

    @Test
    void createProduct_negativePrice_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"iPhone\",\"price\":-5,\"rating\":4.5,\"categoryId\":1}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        verify(productService, never()).createProduct(any(CreateProductRequestDto.class));
    }

    @Test
    void createProduct_ratingTooHigh_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"iPhone\",\"price\":10,\"rating\":99999,\"categoryId\":1}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        verify(productService, never()).createProduct(any(CreateProductRequestDto.class));
    }
}
