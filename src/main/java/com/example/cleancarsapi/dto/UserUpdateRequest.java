package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/me} — the caller's editable profile. {@code email} is the
 * sign-in/invite identity and is not editable here. {@code phone} is stored in E.164
 * ({@code +919876543210}): a number without {@code +} gets the org's country code
 * prefixed (and a leading trunk {@code 0} dropped). Null/blank clears it. Changing the
 * number drops the OTP-verified flag — it must be verified again before buying a plan.
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 32) String phone
) {
}
