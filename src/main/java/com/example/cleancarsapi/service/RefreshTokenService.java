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

    /** Where a refresh came from — only used to make reuse events traceable in the logs. */
    public record ClientInfo(String userAgent, String ip) {
        public static final ClientInfo UNKNOWN = new ClientInfo(null, null);
    }

    private final RefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final Duration reuseGrace;
    private final Duration absoluteTtl;

    /**
     * @param refreshTtlDays   idle lifetime — each rotation extends the token this far
     * @param reuseGraceSeconds how long a just-rotated token may be replayed (lost reply, retry,
     *                          racing tabs) without being treated as theft
     * @param absoluteTtlDays  hard cap on a session family, however actively it is used
     */
    public RefreshTokenService(RefreshTokenRepository refreshTokens,
                               @Value("${app.jwt.refresh-ttl-days:30}") long refreshTtlDays,
                               @Value("${app.jwt.refresh-reuse-grace-seconds:300}") long reuseGraceSeconds,
                               @Value("${app.jwt.refresh-absolute-ttl-days:90}") long absoluteTtlDays) {
        this.refreshTokens = refreshTokens;
        this.ttl = Duration.ofDays(refreshTtlDays);
        this.reuseGrace = Duration.ofSeconds(reuseGraceSeconds);
        this.absoluteTtl = Duration.ofDays(absoluteTtlDays);
    }

    /** Start a new session family (a login). */
    @Transactional
    public String issue(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        return save(userId, UUID.randomUUID(), now, now).raw();
    }

    @Transactional
    public Rotated rotate(String rawToken) {
        return rotate(rawToken, ClientInfo.UNKNOWN);
    }

    @Transactional(noRollbackFor = TokenReuseException.class)
    public Rotated rotate(String rawToken, ClientInfo client) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        LocalDateTime now = LocalDateTime.now();
        boolean revoked = current.getRevokedAt() != null;
        boolean withinGrace = revoked && current.getRevokedAt().plus(reuseGrace).isAfter(now);
        if (revoked && !withinGrace) {
            // A revoked token was replayed after the grace window — treat as theft, but only drop
            // this login's family so the user's other devices stay signed in. noRollbackFor keeps
            // this write when the 401 below propagates.
            log.warn("Reuse of revoked refresh token: user={} family={} revokedAgo={}s ip={} userAgent={}; revoking family",
                    current.getUserId(), current.getFamilyId(),
                    Duration.between(current.getRevokedAt(), now).toSeconds(),
                    client.ip(), client.userAgent());
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new TokenReuseException("Refresh token already used");
        }
        if (current.getExpiresAt().isBefore(now)
                || current.getFamilyStartedAt().plus(absoluteTtl).isBefore(now)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        // Within the grace window a revoked token is almost certainly a concurrent or retried
        // refresh (lost response, parallel requests), not theft: issue a fresh pair in the same
        // family. The token stays revoked, so it cannot extend its own grace.
        if (withinGrace) {
            log.info("Grace replay of refresh token: user={} family={} revokedAgo={}s ip={} userAgent={}",
                    current.getUserId(), current.getFamilyId(),
                    Duration.between(current.getRevokedAt(), now).toSeconds(),
                    client.ip(), client.userAgent());
        } else {
            current.revoke();
        }
        Issued next = save(current.getUserId(), current.getFamilyId(), current.getFamilyStartedAt(), now);
        if (!withinGrace) {
            current.setReplacedBy(next.id());
        }
        return new Rotated(next.raw(), current.getUserId());
    }

    private record Issued(String raw, UUID id) {
    }

    private Issued save(UUID userId, UUID familyId, LocalDateTime familyStartedAt, LocalDateTime now) {
        String raw = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setFamilyId(familyId);
        token.setFamilyStartedAt(familyStartedAt);
        LocalDateTime idleExpiry = now.plus(ttl);
        LocalDateTime absoluteExpiry = familyStartedAt.plus(absoluteTtl);
        token.setExpiresAt(idleExpiry.isBefore(absoluteExpiry) ? idleExpiry : absoluteExpiry);
        refreshTokens.save(token);
        return new Issued(raw, token.getId());
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
