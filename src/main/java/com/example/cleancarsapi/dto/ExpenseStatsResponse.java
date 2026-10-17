package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How an expense sits in its category (rows share the denormalized {@code categoryName}); money is gross.
 * Embedded in {@code GET /api/expenses/{id}}.
 */
public record ExpenseStatsResponse(
        /** Expenses in the category, this one included. */
        long entryCount,
        BigDecimal categoryTotal,
        /** Category spend in the current month (org time zone). */
        BigDecimal categoryThisMonth,
        BigDecimal averageEntry,
        LocalDate firstOn,
        LocalDate lastOn
) {
}
