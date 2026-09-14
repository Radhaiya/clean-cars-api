package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The org's home-dashboard snapshot — no date-range params. All-time job-card
 * counts, today's/yesterday's revenue split by the order's {@code paid} flag, and
 * the current year's month-by-month paid earnings (see {@link MonthlyEarning}).
 */
public record DashboardResponse(
        long servicesInProgress,
        long servicesUnpaid,
        RevenueSplit todayRevenue,
        RevenueSplit yesterdayRevenue,
        List<MonthlyEarning> monthlyEarnings
) {
    /** Net-of-GST revenue for a single day, split by the order's {@code paid} flag. */
    public record RevenueSplit(BigDecimal paid, BigDecimal unpaid) {
    }

    /**
     * One calendar month of the current year. {@code amount} is paid revenue only
     * (net of GST) — {@code null} for a month later than the current one, otherwise
     * the sum for that month (zero if there was none).
     */
    public record MonthlyEarning(int month, BigDecimal amount) {
    }
}
