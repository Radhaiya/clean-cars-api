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
 * {@code allowed_emails} row — the internal console's login whitelist, the single
 * source of truth for "can log in". Email is stored lowercase; comparison is
 * exact-match on the lowercased email. No status column — the only off-switch is
 * another whitelisted user removing the row.
 */
@Entity
@Table(name = "allowed_emails")
@Getter
@Setter
@NoArgsConstructor
public class AllowedEmail {

    @Id
    @UuidGenerator
    private UUID id;

    /** Lowercased, trimmed. */
    @Column(nullable = false, updatable = false)
    private String email;

    /** Who added it (audit) — the caller's token email on {@code POST /internal/api/allowed-emails}. */
    private String createdByEmail;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
