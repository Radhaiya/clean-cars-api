package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.RefreshToken;
import com.example.cleancarsapi.exception.TokenReuseException;
import com.example.cleancarsapi.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens. Not a REST resource — it
 * backs {@link AuthService}'s login / refresh / logout, so it stays a single class.
 */
@Slf4j
@Service
public class RefreshTokenService {

    /** Result of a rotation: the new raw token to hand back, and whose it is. */
    public record Rotated(String rawToken, UUID userId) {
    }

    private final RefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository refreshTokens,
                               @Value("${app.jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.refreshTokens = refreshTokens;
        this.ttl = Duration.ofDays(refreshTtlDays);
    }

    @Transactional
    public String issue(UUID userId) {
        String raw = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        refreshTokens.save(token);
        return raw;
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public Rotated rotate(String rawToken) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (current.getRevokedAt() != null) {
            // A revoked token was replayed — treat as theft and drop every active token for the
            // user. noRollbackFor keeps this write when the 401 below propagates.
            log.warn("Reuse of revoked refresh token for user {}; revoking all sessions", current.getUserId());
            refreshTokens.revokeAllForUser(current.getUserId(), LocalDateTime.now());
            throw new TokenReuseException("Refresh token already used");
        }
        if (current.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        current.revoke();
        UUID userId = current.getUserId();
        String raw = generateRawToken();
        RefreshToken next = new RefreshToken();
        next.setUserId(userId);
        next.setTokenHash(hash(raw));
        next.setExpiresAt(LocalDateTime.now().plus(ttl));
        refreshTokens.save(next);

        return new Rotated(raw, userId);
    }

    /** Idempotent — no error if the token is unknown or already revoked. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokens.revokeAllForUser(userId, LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
