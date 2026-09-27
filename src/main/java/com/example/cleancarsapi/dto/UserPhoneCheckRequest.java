package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for POST /api/me/phone/check — the OTP the user typed, for the phone that was sent one. */
public record UserPhoneCheckRequest(
        @NotBlank @Pattern(regexp = "^\\+\\d{7,15}$", message = "must be E.164, e.g. +919876543210")
        @Size(max = 16) String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code
) {
}
