package com.ecren.billing.config;

import com.ecren.billing.web.MockGatewayInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MockGatewayInterceptor mockGatewayInterceptor;

    public WebMvcConfig(MockGatewayInterceptor mockGatewayInterceptor) {
        this.mockGatewayInterceptor = mockGatewayInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mockGatewayInterceptor);
    }
}
