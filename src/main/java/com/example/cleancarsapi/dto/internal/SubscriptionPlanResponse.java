package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.SubscriptionPlan;

import java.time.LocalDateTime;
import java.util.UUID;

/** Full catalog row for the console editor (no pricing — that lives in Razorpay). Null number = unlimited. */
public record SubscriptionPlanResponse(
        UUID id,
        String name,
        boolean isTrial,
        String razorpayMonthlyPlanId,
        String razorpayYearlyPlanId,
        Integer maxUsers,
        Integer maxCars,
        Integer reportWindowMonths,
        Integer statsRangeYears,
        boolean invoiceGeneration,
        boolean amcEnabled,
        boolean isPublic,
        int sortOrder,
        long subscriptionCount,
        LocalDateTime createdAt
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan p, long subscriptionCount) {
        return new SubscriptionPlanResponse(p.getId(), p.getName(), p.isTrial(),
                p.getRazorpayMonthlyPlanId(), p.getRazorpayYearlyPlanId(),
                p.getMaxUsers(), p.getMaxCars(), p.getReportWindowMonths(), p.getStatsRangeYears(),
                p.isInvoiceGeneration(), p.isAmcEnabled(), p.isPublic(), p.getSortOrder(),
                subscriptionCount, p.getCreatedAt());
    }
}
