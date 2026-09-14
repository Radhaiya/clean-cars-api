package com.example.cleancarsapi.dto;

import java.math.BigDecimal;

/**
 * The dashboard's P&amp;L headline tile for {@code [from, to]} (both inclusive).
 * All three amounts are net of GST (GST collected is a liability, not income; GST
 * paid is generally reclaimable input credit, not a real cost) — {@code totalProfit}
 * is simply {@code totalRevenue - totalExpenses}.
 *
 * <p>{@code totalRevenue}: {@code GstBreakdown.net()} summed across every line item
 * of every <em>paid</em> {@code service_orders} row in range (same as {@code GET
 * /api/charts}). {@code totalExpenses}: same net calculation across every {@code
 * expenses} row in range — expenses have no paid/unpaid concept, all of them count.
 */
public record KpiTilesResponse(
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal totalProfit
) {
}
