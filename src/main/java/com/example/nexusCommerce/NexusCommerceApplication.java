package com.example.nexusCommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
// import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Authentication is JWT-only (see SecurityConfig), so Spring Boot's default in-memory user
// serves nothing but a generated password printed into the startup log.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// @EnableJpaAuditing
public class NexusCommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NexusCommerceApplication.class, args);
	}

}
