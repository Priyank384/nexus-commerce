package com.example.nexusCommerce.configs;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.nexusCommerce.security.LoginRateLimiterInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<LoginRateLimiterInterceptor> loginRateLimiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LoginRateLimiterInterceptor interceptor = loginRateLimiter.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor).addPathPatterns("/api/v1/auth/login");
        }
    }
}
