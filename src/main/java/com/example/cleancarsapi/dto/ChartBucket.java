package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point on the chart: {@code [periodStart, periodEnd]} inclusive, and the metric's
 * value for that period (a plain count for {@code TOTAL_SERVICE}, an amount for
 * {@code TOTAL_REVENUE}). Always present even when the count/amount is zero, so the
 * UI can render a continuous axis.
 */
public record ChartBucket(LocalDate periodStart, LocalDate periodEnd, BigDecimal value) {
}
