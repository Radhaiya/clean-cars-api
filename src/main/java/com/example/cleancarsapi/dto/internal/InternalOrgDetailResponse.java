package com.example.cleancarsapi.dto.internal;

import java.util.List;
import java.util.UUID;

/**
 * The full org view — everything on the summary card plus the embedded member
 * list, the live subscription row (with plan block, no pricing), all-time
 * totals, and the most recent payments (paise converted to rupee decimals).
 */
public record InternalOrgDetailResponse(
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
        InternalSubscriptionResponse subscription,
        List<InternalUserResponse> users,
        InternalOrgTotalsResponse totals,
        List<InternalPaymentResponse> recentPayments
) {
}
