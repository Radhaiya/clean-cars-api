package com.example.cleancarsapi.dto;

/**
 * Bucket size for {@code GET /api/charts}. Not persisted — a request-shape enum only.
 * Buckets are calendar-aligned: {@code WEEK} is the ISO week (Monday–Sunday), {@code MONTH}
 * and {@code YEAR} are calendar months/years — the first/last bucket may extend slightly
 * beyond the requested {@code from}/{@code to} to cover the whole period.
 */
public enum ChartGranularity {
    DAY,
    WEEK,
    MONTH,
    YEAR
}
