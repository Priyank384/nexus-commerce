package com.example.nexusCommerce.integration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nexusCommerce.services.cache.ProductRedisCache;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.autoconfigure.exclude="+
    "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
    "org.springframework.boot.autoconfigure.data.redis.DataRedisReactiveAutoConfiguration," +
    "org.springframework.boot.autoconfigure.data.redis.DataRedisRepositoriesAutoConfiguration"
)
@AutoConfigureMockMvc
public class EcomFlowIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductRedisCache productRedisCache;

    @BeforeEach
    void setUp(){
        when(productRedisCache.getSummary(anyLong())).thenReturn(Optional.empty());
    }


}
