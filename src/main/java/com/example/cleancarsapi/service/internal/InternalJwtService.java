package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.security.internal.InternalConsoleUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Issues the internal console's signed access token: {@code sub} is the Firebase
 * uid (not a UUID — the console user has no users row), plus the verified
 * whitelisted email. Signs with the internal secret / issuer
 * ({@code clean-cars-api-internal}) — a tenant token can never open
 * {@code /internal/**} and vice-versa. Owns its encoder directly (nothing else
 * needs it), so no JwtEncoder bean is created for it.
 */
@Service
public class InternalJwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long ttlSeconds;

    public InternalJwtService(@Value("${app.internal.jwt.secret}") String secret,
                              @Value("${app.internal.jwt.issuer}") String issuer,
                              @Value("${app.internal.jwt.ttl-seconds:3600}") long ttlSeconds) {
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String issueToken(InternalConsoleUser user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(user.uid())
                .claim("email", user.email())
                .claim("name", user.name())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
