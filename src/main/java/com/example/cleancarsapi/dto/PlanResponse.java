package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.SubscriptionPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A plan as shown on the pricing / upgrade screen.
 *
 * <p>{@code pricing.currency} is hard-coded {@code "INR"} for now — TODO(cashfree):
 * read it from the Cashfree plan once billing is wired. The monthly/yearly toggle
 * on the UI just switches between {@code pricing.monthly} and {@code pricing.yearly};
 * {@code pricing.yearly} is absent when the plan has no yearly offer. {@code isTrial}
 * marks the single dedicated, one-time-usable Trial plan (its {@code trialDays} is the
 * fixed length; {@code null} for every other plan). In {@code limits} / {@code features}
 * a {@code null} number means "unlimited".
 */
public record PlanResponse(
        Long id,
        String name,
        Pricing pricing,
        boolean isTrial,
        Integer trialDays,
        Limits limits,
        Features features
) {
    public record Pricing(String currency, Monthly monthly, Yearly yearly) {
    }

    public record Monthly(BigDecimal price) {
    }

    /** {@code pricePerMonth} = yearly / 12; {@code savingsPercent} vs paying monthly for a year (0 if none). */
    public record Yearly(BigDecimal price, BigDecimal pricePerMonth, int savingsPercent) {
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

    /** @param trialDays the fixed one-time trial length (see {@code SubscriptionService.TRIAL_DAYS}); ignored unless {@code p.isTrial()}. */
    public static PlanResponse from(SubscriptionPlan p, int trialDays) {
        boolean statisticsPage = p.getStatsRangeYears() == null || p.getStatsRangeYears() > 0;
        return new PlanResponse(
                p.getId(),
                p.getName(),
                new Pricing("INR", new Monthly(p.getMonthlyPrice()), yearly(p.getMonthlyPrice(), p.getYearlyPrice())),
                p.isTrial(),
                p.isTrial() ? trialDays : null,
                new Limits(p.getMaxUsers(), p.getMaxCars()),
                new Features(p.getReportWindowMonths(), p.getStatsRangeYears(), statisticsPage, p.isInvoiceGeneration()));
    }

    private static Yearly yearly(BigDecimal monthly, BigDecimal yearly) {
        if (yearly == null) {
            return null;
        }
        BigDecimal perMonth = yearly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal yearAtMonthly = monthly.multiply(BigDecimal.valueOf(12));
        int savings = yearAtMonthly.signum() <= 0 ? 0
                : yearAtMonthly.subtract(yearly)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(yearAtMonthly, 0, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO)
                        .intValue();
        return new Yearly(yearly, perMonth, savings);
    }
}
