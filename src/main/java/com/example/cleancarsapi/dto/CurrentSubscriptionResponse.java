package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * The caller org's current plan. Always 200. {@code status} is {@code "NONE"} (org-less or
 * never subscribed) or the latest subscription row's real {@link SubscriptionStatus} name —
 * terminal rows (CANCELLED / EXPIRED) report their actual status with {@code active=false}.
 * {@code statuses} carries every possible {@link SubscriptionStatus} value so the frontend
 * never hardcodes the enum. {@code onTrial} is a convenience for {@code status == TRIALING}.
 */
public record CurrentSubscriptionResponse(
        boolean active,
        boolean onTrial,
        String status,
        Long subscriptionId,
        LocalDate startDate,
        LocalDate endDate,
        Long daysRemaining,
        /** MONTHLY / YEARLY for paid rows; null while trialing (no billing cycle to sell). */
        String billingCycle,
        PlanResponse plan,
        /** All possible subscription status values (TRIALING, PENDING, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED, EXPIRED). */
        List<String> statuses
) {
    private static final List<String> ALL_STATUSES = Arrays.stream(SubscriptionStatus.values())
            .map(Enum::name)
            .toList();

    public static CurrentSubscriptionResponse none() {
        return new CurrentSubscriptionResponse(false, false, "NONE", null, null, null, null, null, null, ALL_STATUSES);
    }

    /** @param trialDays the fixed one-time trial length (see {@code SubscriptionService.TRIAL_DAYS}). */
    public static CurrentSubscriptionResponse of(Subscription s, SubscriptionPlan plan, int trialDays) {
        long remaining = s.getEndDate() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate()));
        // Pricing comes from GET /api/plans only (live Razorpay fetch) — embedded plan blocks carry no prices.
        return new CurrentSubscriptionResponse(
                isLive(s.getStatus()),
                s.getStatus() == SubscriptionStatus.TRIALING,
                s.getStatus().name(),
                s.getId(),
                s.getStartDate(),
                s.getEndDate(),
                remaining,
                s.getBillingCycle() == null ? null : s.getBillingCycle().name(),
                PlanResponse.from(plan, trialDays, null),
                ALL_STATUSES);
    }

    private static boolean isLive(SubscriptionStatus status) {
        return status == SubscriptionStatus.TRIALING
                || status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE;
    }
}
