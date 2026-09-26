package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The org's home-dashboard snapshot — no date-range params. All-time job-card
 * counts (in-progress split by car/bike), the unpaid book, today's/yesterday's
 * revenue split by the order's {@code paid} flag, the rolling 30-day customer
 * trend, the current year's {@link MonthlyFinancial month-by-month} earnings
 * vs expenses, today's job cards, and the recently served customers.
 */
public record DashboardResponse(
        long servicesInProgress,
        long carsInProgress,
        long bikesInProgress,
        long servicesUnpaid,
        BigDecimal unpaidAmount,
        RevenueSplit todayRevenue,
        RevenueSplit yesterdayRevenue,
        BigDecimal todayRevenueChangePct,
        long totalCustomers,
        long newCustomersLast30Days,
        long newCustomersPrevious30Days,
        BigDecimal customersChangePct,
        List<MonthlyFinancial> monthlyFinancials,
        List<DashboardServiceRow> todaysServices,
        List<DashboardRecentCustomer> recentCustomers
) {
    /** Net-of-tax revenue for a single day, split by the order's {@code paid} flag. */
    public record RevenueSplit(BigDecimal paid, BigDecimal unpaid) {
    }

    /**
     * One calendar month. {@code earnings} is paid revenue only (net of tax);
     * {@code expenses} is the gross (tax-inclusive) total of every expense
     * recorded that month. Both {@code null} for a month later than the current
     * one, otherwise the sum for that month (zero if there was none).
     */
    public record MonthlyFinancial(int month, BigDecimal earnings, BigDecimal expenses) {
    }
}
