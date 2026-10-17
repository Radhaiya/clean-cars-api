package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Usage of one catalog service across the org's non-cancelled service orders. Order lines keep only a
 * name snapshot (no catalog id), so lines are matched to the service by name (case-insensitive) — renaming
 * a service starts its history afresh. Money is gross (tax included, after line discounts).
 */
public record ServiceCatalogStatsResponse(
        /** Distinct orders containing the service. */
        long orderCount,
        /** Sum of line quantities. */
        long unitsSold,
        BigDecimal totalRevenue,
        /** Revenue in the current month (org time zone). */
        BigDecimal revenueThisMonth,
        /** Revenue per order containing it (0 when never used). */
        BigDecimal averageOrderValue,
        /** Total discount given on its lines. */
        BigDecimal totalDiscount,
        LocalDate firstUsedOn,
        LocalDate lastUsedOn
) {
}
