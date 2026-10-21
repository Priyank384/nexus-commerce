package com.example.nexusCommerce.configs;

import java.nio.file.Paths;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.nexusCommerce.security.LoginRateLimiterInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<LoginRateLimiterInterceptor> loginRateLimiter;

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LoginRateLimiterInterceptor interceptor = loginRateLimiter.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor).addPathPatterns("/api/v1/auth/login");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded product images back over HTTP from the local store. In prod,
        // swap this for a CDN or S3 by replacing the resource location here.
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(location);
    }
}
