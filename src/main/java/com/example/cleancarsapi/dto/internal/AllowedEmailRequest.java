package com.example.cleancarsapi.dto.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** {@code POST /internal/api/allowed-emails} — the email to whitelist. */
public record AllowedEmailRequest(
        @NotBlank @Email String email) {
}
