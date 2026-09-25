package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.internal.AllowedEmail;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code allowed_emails} row, as listed by {@code GET /internal/api/allowed-emails}. */
public record AllowedEmailResponse(UUID id, String email, String createdByEmail, LocalDateTime createdAt) {

    public static AllowedEmailResponse from(AllowedEmail row) {
        return new AllowedEmailResponse(row.getId(), row.getEmail(), row.getCreatedByEmail(), row.getCreatedAt());
    }
}
