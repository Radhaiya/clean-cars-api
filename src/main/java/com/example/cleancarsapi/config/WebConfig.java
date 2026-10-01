package com.example.cleancarsapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final MaintenanceModeInterceptor maintenanceModeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceModeInterceptor)
                .addPathPatterns("/api/**")
                // The UI must still learn maintenance is on; a logged-in session must
                // survive it (refresh/logout); Razorpay's money events must still land.
                .excludePathPatterns("/api/properties", "/api/auth/refresh", "/api/auth/logout",
                        "/api/webhooks/razorpay", "/api/reference");
    }
}
