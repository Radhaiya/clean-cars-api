package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.entity.internal.InternalRefreshToken;
import com.example.cleancarsapi.exception.TokenReuseException;
import com.example.cleancarsapi.repository.internal.InternalRefreshTokenRepository;
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

/**
 * Issues, rotates and revokes the internal console's opaque refresh tokens —
 * the tenant {@code RefreshTokenService} ported verbatim, keyed by email
 * instead of userId (a console user need not correspond to any users row).
 * Not a REST resource — it backs {@link InternalAuthService}.
 */
@Slf4j
@Service
public class InternalRefreshTokenService {

    /** Result of a rotation: the new raw token to hand back, and whose it is. */
    public record Rotated(String rawToken, String firebaseUid, String email) {
    }

    private final InternalRefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public InternalRefreshTokenService(InternalRefreshTokenRepository refreshTokens,
                                       @Value("${app.internal.jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.refreshTokens = refreshTokens;
        this.ttl = Duration.ofDays(refreshTtlDays);
    }

    @Transactional
    public String issue(String email, String firebaseUid) {
        String raw = generateRawToken();
        InternalRefreshToken token = new InternalRefreshToken();
        token.setEmail(email);
        token.setFirebaseUid(firebaseUid);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        refreshTokens.save(token);
        return raw;
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public Rotated rotate(String rawToken) {
        InternalRefreshToken current = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (current.getRevokedAt() != null) {
            // A revoked token was replayed — treat as theft and drop every active token for the
            // email. noRollbackFor keeps this write when the 401 below propagates.
            log.warn("Reuse of revoked internal refresh token for {}; revoking all sessions", current.getEmail());
            refreshTokens.revokeAllForEmail(current.getEmail(), LocalDateTime.now());
            throw new TokenReuseException("Refresh token already used");
        }
        if (!current.isActive()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        current.revoke();
        String email = current.getEmail();
        String firebaseUid = current.getFirebaseUid();
        String raw = generateRawToken();
        InternalRefreshToken next = new InternalRefreshToken();
        next.setEmail(email);
        next.setFirebaseUid(firebaseUid);
        next.setTokenHash(hash(raw));
        next.setExpiresAt(LocalDateTime.now().plus(ttl));
        refreshTokens.save(next);

        return new Rotated(raw, firebaseUid, email);
    }

    /** Idempotent — no error if the token is unknown or already revoked. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(InternalRefreshToken::revoke);
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
