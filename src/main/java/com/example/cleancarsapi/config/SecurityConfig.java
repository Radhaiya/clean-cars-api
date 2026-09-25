package com.example.cleancarsapi.config;

import com.example.cleancarsapi.security.AuthenticatedUserJwtConverter;
import com.example.cleancarsapi.security.internal.InternalConsoleUserJwtConverter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stateless security with two token worlds in one process (mirroring the
 * internal console plan — see the repo's docs). Chain 1 matches
 * {@code /internal/**} and verifies internal-console tokens (their own secret +
 * issuer {@code clean-cars-api-internal}) into an
 * {@code InternalConsoleUser} principal; chain 2 (the fallback) is the tenant
 * API on {@code /api/**}. The two secrets/issuers are distinct by design so a
 * token meant for one service can never authenticate to the other.
 * <p>
 * Permit-all: {@code /api/auth/**}, {@code /internal/api/auth/**} (both
 * Firebase-verified), the Razorpay webhook (its own HMAC check) and
 * {@code /api/reference} for tenant callers.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final SecretKeySpec signingKey;
    private final SecretKeySpec internalSigningKey;
    private final String internalIssuer;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret,
                          @Value("${app.internal.jwt.secret}") String internalSecret,
                          @Value("${app.internal.jwt.issuer}") String internalIssuer) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.internalSigningKey = new SecretKeySpec(internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.internalIssuer = internalIssuer;
    }

    /** Internal console chain — matched FIRST, on the /internal/** path prefix only. */
    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
                                                    InternalConsoleUserJwtConverter jwtConverter) throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/api/auth/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                    jwt.decoder(internalJwtDecoder());
                    jwt.jwtAuthenticationConverter(jwtConverter);
                }))
                .build();
    }

    /** Tenant chain — the fallback (no explicit matcher), i.e. everything except /internal/**. */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticatedUserJwtConverter jwtConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/webhooks/razorpay", "/api/reference", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }

    /** Picked up automatically by {@code .cors(Customizer.withDefaults())} above. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(props.allowedOriginPatterns());
        configuration.setAllowedMethods(props.allowedMethods());
        configuration.setAllowedHeaders(props.allowedHeaders());
        configuration.setAllowCredentials(props.allowCredentials());
        configuration.setMaxAge(props.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /** Not a bean — the internal token world stays private to the one chain above. */
    private JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(internalSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(internalIssuer));
        return decoder;
    }
}
