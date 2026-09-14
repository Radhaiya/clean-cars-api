package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The caller org's current plan. Always 200: when the org has no live
 * subscription (never subscribed, or the trial/subscription ended) {@code active}
 * is false, {@code status} is {@code "NONE"} and every other field is null.
 *
 * <p>{@code status} is {@code "NONE"} or a live {@link SubscriptionStatus} name
 * (TRIALING / ACTIVE / PAST_DUE); terminal states are never returned here.
 * {@code onTrial} is a convenience for {@code status == TRIALING}.
 */
public record CurrentSubscriptionResponse(
        boolean active,
        boolean onTrial,
        String status,
        Long subscriptionId,
        LocalDate startDate,
        LocalDate endDate,
        Long daysRemaining,
        PlanResponse plan
) {
    public static CurrentSubscriptionResponse none() {
        return new CurrentSubscriptionResponse(false, false, "NONE", null, null, null, null, null);
    }

    /** @param trialDays the fixed one-time trial length (see {@code SubscriptionService.TRIAL_DAYS}). */
    public static CurrentSubscriptionResponse of(Subscription s, SubscriptionPlan plan, int trialDays) {
        long remaining = s.getEndDate() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate()));
        return new CurrentSubscriptionResponse(
                true,
                s.getStatus() == SubscriptionStatus.TRIALING,
                s.getStatus().name(),
                s.getId(),
                s.getStartDate(),
                s.getEndDate(),
                remaining,
                PlanResponse.from(plan, trialDays));
    }
}
