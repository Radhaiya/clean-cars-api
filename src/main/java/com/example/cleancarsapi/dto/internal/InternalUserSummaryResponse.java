package com.example.cleancarsapi.dto.internal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One card per user on the cross-org console list, including the user's plan
 * state: {@code planStatus} is the org's live subscription status — only
 * TRIALING/ACTIVE/PAST_DUE count; terminal/absent rows are {@code NONE},
 * {@code planName}/{@code planExpiryDate}/{@code trialDaysRemaining} omitted when NONE.
 */
public record InternalUserSummaryResponse(
        UUID id,
        String email,
        UUID orgId,
        String orgName,
        String role,
        boolean trialUsed,
        String status,
        String createdAt,
        String planName,
        String planStatus,
        LocalDate planExpiryDate,
        Long trialDaysRemaining
) {
}
