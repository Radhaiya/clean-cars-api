package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
/** An org's subscription as returned after starting a trial. */
public record SubscriptionResponse(
        UUID id,
        UUID planId,
        String planName,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int trialDays,
        long daysRemaining
) {
    /** @param trialDays the fixed one-time trial length (see {@code SubscriptionService.TRIAL_DAYS}). */
    public static SubscriptionResponse of(Subscription s, SubscriptionPlan plan, int trialDays) {
        long remaining = s.getEndDate() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate()));
        return new SubscriptionResponse(
                s.getId(),
                s.getPlanId(),
                plan.getName(),
                s.getStatus(),
                s.getStartDate(),
                s.getEndDate(),
                trialDays,
                remaining);
    }
}
