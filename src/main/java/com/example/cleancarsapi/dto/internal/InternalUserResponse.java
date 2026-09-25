package com.example.cleancarsapi.dto.internal;

import java.util.UUID;

/** One members-row of an org in the console ({@code users} table, no Firebase uid). */
public record InternalUserResponse(
        UUID id,
        String email,
        String phone,
        String role,
        boolean trialUsed,
        String status,
        String createdAt
) {
}
