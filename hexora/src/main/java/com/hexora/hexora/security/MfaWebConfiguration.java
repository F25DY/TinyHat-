package com.hexora.hexora.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MfaWebConfiguration implements WebMvcConfigurer {

    private final RecentAuthenticationInterceptor recentAuthenticationInterceptor;

    public MfaWebConfiguration(RecentAuthenticationInterceptor recentAuthenticationInterceptor) {
        this.recentAuthenticationInterceptor = recentAuthenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(recentAuthenticationInterceptor);
    }
}
