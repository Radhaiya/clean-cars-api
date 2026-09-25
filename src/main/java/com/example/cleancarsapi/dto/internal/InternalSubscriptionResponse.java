package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.BillingCycle;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One {@code subscriptions} row as the console sees it — the full history is
 * listed on {@code GET /internal/api/subscriptions}; the live row is embedded
 * in org/user views ({@code status} omitted = no live subscription).
 */
public record InternalSubscriptionResponse(
        UUID id,
        UUID orgId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long daysRemaining,
        String billingCycle,
        InternalPlanResponse plan
) {
    public static InternalSubscriptionResponse of(Subscription sub, SubscriptionPlan plan) {
        Long remaining = sub.getEndDate() == null ? null
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sub.getEndDate()));
        BillingCycle cycle = sub.getBillingCycle();
        return new InternalSubscriptionResponse(
                sub.getId(),
                sub.getOrgId(),
                sub.getStatus().name(),
                sub.getStartDate(),
                sub.getEndDate(),
                remaining,
                cycle == null ? null : cycle.name(),
                InternalPlanResponse.from(plan));
    }
}
