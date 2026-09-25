package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.SubscriptionPlan;

import java.util.UUID;

/**
 * An embedded plan block on the console — the capability column view, with no
 * pricing: pricing belongs to a live Razorpay fetch ({@code GET /api/plans}) and
 * the console takes no dependency on Razorpay availability. A null number means
 * "unlimited".
 */
public record InternalPlanResponse(
        UUID id,
        String name,
        boolean isTrial,
        Integer maxUsers,
        Integer maxCars,
        Integer reportWindowMonths,
        Integer statsRangeYears,
        boolean invoiceGeneration
) {
    public static InternalPlanResponse from(SubscriptionPlan plan) {
        return new InternalPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.isTrial(),
                plan.getMaxUsers(),
                plan.getMaxCars(),
                plan.getReportWindowMonths(),
                plan.getStatsRangeYears(),
                plan.isInvoiceGeneration());
    }
}
