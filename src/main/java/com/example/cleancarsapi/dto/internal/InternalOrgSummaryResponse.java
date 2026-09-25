package com.example.cleancarsapi.dto.internal;

import java.util.UUID;

/**
 * One card per org on the console list. {@code subscriptionStatus} is the live
 * subscription's status (mirrors {@code CurrentSubscriptionResponse} semantics
 * — only TRIALING/ACTIVE/PAST_DUE count) or {@code NONE}.
 */
public record InternalOrgSummaryResponse(
        UUID id,
        String name,
        String timezone,
        String currencyCode,
        String currencySymbol,
        String contactPhone,
        String contactEmail,
        String createdAt,
        long memberCount,
        String subscriptionStatus,
        String planName
) {
    public static final String NO_SUBSCRIPTION = "NONE";
}
