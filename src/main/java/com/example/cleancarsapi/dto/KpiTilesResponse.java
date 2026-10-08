package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The dashboard's P&amp;L headline tile for {@code [from, to]} (both inclusive).
 * All three amounts are net of tax (tax collected is a liability, not income; tax
 * paid is generally reclaimable input credit, not a real cost) — {@code totalProfit}
 * is {@code totalRevenue + amcRevenue - totalExpenses}.
 *
 * <p>{@code totalRevenue}: {@code TaxBreakdown.net()} summed across every line item
 * of every <em>paid</em> {@code service_orders} row in range (same as {@code GET
 * /api/charts}). {@code totalExpenses}: same net calculation across every {@code
 * expenses} row in range — expenses have no paid/unpaid concept, all of them count.
 * {@code totalServices}: count of non-{@code CANCELLED} job cards created in range
 * (same definition as the {@code TOTAL_SERVICE} chart metric). {@code
 * averageServiceValue}: {@code totalRevenue} divided by the count of paid orders in
 * range (0.00 when there are none) — a range-scoped average ticket size, comparable
 * month over month. {@code newCustomers}: customers whose record was first created
 * in range (a proxy for their first-ever visit). {@code revenueByPaymentType}: the
 * same paid, net-of-tax revenue split by {@code paymentType}'s name — always has an
 * entry for every {@link PaymentType} (0.00 when unused) plus an {@code UNCATEGORIZED}
 * entry for paid orders with no payment type recorded (still counted in {@code
 * totalRevenue}).
 * {@code expensesByCategory}: {@code totalExpenses} split by the expense's
 * (denormalized, free-text) {@code categoryName}, ranked highest first — only
 * categories actually used in range appear, unlike the fixed {@code PaymentType} set.
 * {@code amcRevenue}: net-of-tax {@code saleNet} summed across every AMC sold (by payment
 * date) in range; reported separately from {@code totalRevenue} but counted in {@code totalProfit}.
 * {@code revenueByEmployee}: each employee's job count and paid net-of-tax revenue in
 * range, ranked by revenue highest first, plus an "Unassigned" row for orders with no
 * assigned employee — see {@link EmployeeRevenueResponse}.
 */
public record KpiTilesResponse(
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal totalProfit,
        long totalServices,
        BigDecimal averageServiceValue,
        long newCustomers,
        BigDecimal amcRevenue,
        Map<String, BigDecimal> revenueByPaymentType,
        Map<String, BigDecimal> expensesByCategory,
        List<EmployeeRevenueResponse> revenueByEmployee
) {
}
