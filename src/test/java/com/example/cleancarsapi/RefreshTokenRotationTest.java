package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.RefreshToken;
import com.example.cleancarsapi.exception.TokenReuseException;
import com.example.cleancarsapi.repository.RefreshTokenRepository;
import com.example.cleancarsapi.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenRotationTest {

    private RefreshTokenRepository repo;
    private RefreshTokenService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repo, 30, 20);
    }

    private RefreshToken token(LocalDateTime revokedAt) {
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash("h");
        t.setExpiresAt(LocalDateTime.now().plusDays(1));
        t.setRevokedAt(revokedAt);
        return t;
    }

    @Test
    void activeTokenRotatesAndIsRevoked() {
        RefreshToken t = token(null);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(t));

        var rotated = service.rotate("raw");

        assertNotNull(t.getRevokedAt());
        assertNotNull(rotated.rawToken());
        assertEquals(userId, rotated.userId());
        verify(repo, never()).revokeAllForUser(any(), any());
    }

    @Test
    void recentlyRevokedTokenGetsFreshPairWithoutLockout() {
        RefreshToken t = token(LocalDateTime.now().minusSeconds(5));
        LocalDateTime originalRevokedAt = t.getRevokedAt();
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(t));

        var rotated = service.rotate("raw");

        assertNotNull(rotated.rawToken());
        assertEquals(originalRevokedAt, t.getRevokedAt(), "grace must not extend revocation time");
        verify(repo, never()).revokeAllForUser(any(), any());
    }

    @Test
    void staleRevokedTokenTriggersLockout() {
        RefreshToken t = token(LocalDateTime.now().minusSeconds(60));
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(t));

        assertThrows(TokenReuseException.class, () -> service.rotate("raw"));
        verify(repo).revokeAllForUser(any(), any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> service.rotate("raw"));
    }
}
