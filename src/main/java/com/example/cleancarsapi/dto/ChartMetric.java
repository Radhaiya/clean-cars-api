package com.example.cleancarsapi.dto;

/** Which number {@code GET /api/charts} buckets up. Not persisted — a request-shape enum only. */
public enum ChartMetric {
    TOTAL_SERVICE,
    TOTAL_REVENUE,
    /** Net AMC sales by payment date (not after today) — drawn beside {@code TOTAL_REVENUE} on the Statistics chart. */
    AMC_REVENUE,
    /** Per bucket: service-order revenue + AMC sales − expenses, all net of tax (can be negative). */
    TOTAL_PROFIT
}
