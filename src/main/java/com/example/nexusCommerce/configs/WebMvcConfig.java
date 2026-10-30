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
            // Refresh is not listed: every page load refreshes the session, and behind the
            // BFF that traffic shares one IP, so limiting it would lock shoppers out.
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/api/v1/auth/login", "/api/v1/users");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serves images written by LocalImageStorage. When STORAGE_TYPE=s3 the product
        // rows point straight at the bucket, so this handler simply goes unused.
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(location);
    }
}
