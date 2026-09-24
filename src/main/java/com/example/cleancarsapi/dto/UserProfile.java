package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import java.util.UUID;
/**
 * Safe view of the authenticated user — never exposes the password hash.
 * {@code plan} is present whenever the org has a subscription row (live or terminal) —
 * {@code null} for an org-less caller or one who never subscribed. Its {@code status}
 * carries the real {@code SubscriptionStatus} (e.g. CANCELLED / HALTED) so the UI can
 * react to non-live states.
 */
public record UserProfile(
        UUID id,
        String name,
        String email,
        String phone,
        UserRole role,
        String status,
        /**
         * True when this account is a managed member of an org — joined via an
         * invite that was accepted ({@code User.isManagedMember}). The UI uses it
         * to configure screens: owner screens (invites, org settings) only for
         * owners; members get the org's operational workspace. {@code false} for
         * the owner and for org-less callers — always serialized as a boolean.
         */
        boolean isManaged,
        UUID orgId,
        String orgName,
        String orgTimezone,
        PlanUsage plan
) {
    /**
     * {@code maxUsers} / {@code maxCars} / {@code reportWindowMonths} / {@code statsRangeYears}
     * null = unlimited (same convention as {@code PlanResponse.Limits}/{@code Features});
     * {@code statsRangeYears} 0 = statistics page hidden entirely. {@code billingCycle}
     * is the cycle sold (MONTHLY/YEARLY); null while on trial. {@code status} is the
     * subscription's real state (TRIALING / PENDING / ACTIVE / PAST_DUE /
     * HALTED / CANCELLED / EXPIRED) — not necessarily live, so the UI can show
     * cancelled/halted differently. Serialized as its name (uppercase).
     * {@code maxCars} is a shared vehicle cap — the UI weighs {@code currentCars}
     * + {@code currentBikes} against it.
     */
    public record PlanUsage(
            String planName,
            boolean isTrial,
            String billingCycle,
            SubscriptionStatus status,
            Integer maxUsers,
            long currentUsers,
            Integer maxCars,
            long currentCars,
            long currentBikes,
            Integer reportWindowMonths,
            Integer statsRangeYears
    ) {
    }

    public static UserProfile of(User user, String orgName, String orgTimezone, PlanUsage plan) {
        return new UserProfile(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.isManagedMember(),
                user.getOrgId(),
                orgName,
                orgTimezone,
                plan);
    }
}
