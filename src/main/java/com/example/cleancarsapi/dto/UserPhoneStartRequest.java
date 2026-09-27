package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body for POST /api/me/phone/start — the phone to verify (E.164) and the channel
 * the OTP goes out on. The phone is supplied per request (not read off the account)
 * because Google/Apple sign-ups have no stored number yet; an approved check
 * persists it as the account's verified phone.
 */
public record UserPhoneStartRequest(
        @NotBlank @Pattern(regexp = "^\\+\\d{7,15}$", message = "must be E.164, e.g. +919876543210")
        @jakarta.validation.constraints.Size(max = 16) String phone,
        @NotBlank @Pattern(regexp = "sms|whatsapp", flags = Pattern.Flag.CASE_INSENSITIVE)
        String channel
) {
}
