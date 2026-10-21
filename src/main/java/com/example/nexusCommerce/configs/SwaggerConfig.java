package com.example.nexusCommerce.configs;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                .title("NexusCommerce API")
                .version("1.0")
                .description("E-commerce API: catalogue, orders, reviews, users, auth and admin"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .tags(List.of(
                        new Tag().name("Auth").description("Registration and login; issues the JWT bearer token"),
                        new Tag().name("Users").description("User account operations"),
                        new Tag().name("Products").description("Product catalogue and inventory"),
                        new Tag().name("Categories").description("Category catalogue"),
                        new Tag().name("Reviews").description("Product reviews and moderation"),
                        new Tag().name("Orders").description("Place and manage orders"),
                        new Tag().name("Admin").description("Administrative operations - dashboard metrics, user management, and stock control (ROLE_ADMIN)")));
    }
}
