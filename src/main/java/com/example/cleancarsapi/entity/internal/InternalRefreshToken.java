package com.example.cleancarsapi.entity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code internal_refresh_tokens} row — one issued internal-console refresh
 * token. Same design as the tenant {@code RefreshToken} (only the SHA-256 hash
 * stored, rotate on refresh, replay of a revoked one revokes the user's whole
 * set) but keyed by email, since a console user need not correspond to any
 * {@code users} row.
 */
@Entity
@Table(name = "internal_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class InternalRefreshToken {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String email;

    /** The identity's Firebase uid — carried through rotation into refreshed tokens' {@code sub}. */
    private String firebaseUid;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
