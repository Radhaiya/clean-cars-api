package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body for POST /api/me/email/check — the 6-digit code emailed to the account address. */
public record UserEmailCheckRequest(@NotBlank @Pattern(regexp = "^\\d{6}$") String code) {
}
