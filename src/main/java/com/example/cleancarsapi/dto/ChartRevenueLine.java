package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One service-order-item line for revenue charting: the parent order's
 * {@code createdAt} (the bucket key) plus the raw GST inputs needed to derive its
 * net amount via {@link GstBreakdown}. Internal to {@code ChartService} — not returned
 * from any endpoint as-is.
 */
public record ChartRevenueLine(
        LocalDateTime orderCreatedAt,
        BigDecimal basePrice,
        BigDecimal gstPercentage,
        boolean gstIncluded,
        int quantity
) {
}
