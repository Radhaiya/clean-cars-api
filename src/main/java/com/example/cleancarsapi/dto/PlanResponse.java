package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.SubscriptionPlan;

import java.math.BigDecimal;
import com.example.cleancarsapi.service.RazorpayGateway;
import java.util.UUID;
/**
 * A plan as shown on the pricing / upgrade screen.
 *
 * <p>Prices live in Razorpay. Each billing cycle is a separate Razorpay Plan
 * (Razorpay has no "plan with two prices"); {@code pricing.monthly} /
 * {@code pricing.yearly} are built from live Razorpay fetches and are absent
 * when the plan has no Razorpay plan for that cycle (Trial has none). The
 * monthly/yearly toggle on the UI switches between the two cycles.
 *
 * <p>{@code isTrial} marks the single dedicated, one-time-usable Trial plan
 * ({@code trialDays} is the fixed length; null for every other plan). In
 * {@code limits} / {@code features} a null number means "unlimited".
 */
public record PlanResponse(
        UUID id,
        String name,
        Pricing pricing,
        boolean isTrial,
        Integer trialDays,
        Limits limits,
        Features features
) {
    /** One billing cycle's live Razorpay data (absent when the plan has no Razorpay plan for that cycle). */
    public record Pricing(Cycle monthly, Cycle yearly) {
    }

    /**
     * @param razorpayPlanId the Razorpay Plan ID to hand the checkout flow for this cycle
     * @param amount         price in rupees, fetched live from Razorpay (null when not fetched
     *                       live — {@code CurrentSubscriptionResponse} embeds the plan without prices)
     */
    public record Cycle(String razorpayPlanId, BigDecimal amount, String currency) {
    }

    /** null = unlimited. */
    public record Limits(Integer maxUsers, Integer maxCars) {
    }

    /** {@code reportWindowMonths}/{@code statsRangeYears} null = unlimited; {@code statisticsPage} false when the page is hidden. */
    public record Features(
            Integer reportWindowMonths,
            Integer statsRangeYears,
            boolean statisticsPage,
            boolean invoiceGeneration
    ) {
    }

    /**
     * @param trialDays the fixed one-time trial length (see {@code SubscriptionService.TRIAL_DAYS}); ignored unless {@code p.isTrial()}
     * @param pricing   live Razorpay cycle pricing, or null to omit the block entirely
     *                  (embedded plan blocks read nothing — pricing comes from GET /api/plans only)
     */
    public static PlanResponse from(SubscriptionPlan p, int trialDays, Pricing pricing) {
        boolean statisticsPage = p.getStatsRangeYears() == null || p.getStatsRangeYears() > 0;
        return new PlanResponse(
                p.getId(),
                p.getName(),
                pricing,
                p.isTrial(),
                p.isTrial() ? trialDays : null,
                new Limits(p.getMaxUsers(), p.getMaxCars()),
                new Features(p.getReportWindowMonths(), p.getStatsRangeYears(), statisticsPage, p.isInvoiceGeneration()));
    }
}
