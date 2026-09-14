package com.example.cleancarsapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS policy, environment-specific — see {@code app.cors.*} in
 * {@code application-{local,stage,prod}.yml}. Origins are matched as patterns
 * (supports {@code http://localhost:*}), not exact strings.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        long maxAge) {
}
