package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;

import java.math.BigDecimal;

/**
 * Query projection — one paid order-item line carrying its order's payment
 * type, for kpi-tiles' revenue-by-payment-type breakdown. Replaces {@link
 * ChartRevenueLine} as the kpi-tiles fetch (same paid-orders-in-range
 * population, {@code paymentType} instead of {@code orderCreatedAt}). Internal
 * — not returned to clients as-is.
 */
public record PaymentTypeRevenueRow(
        PaymentType paymentType,
        BigDecimal basePrice,
        BigDecimal taxPercentage,
        boolean taxIncluded,
        int quantity
) {
}
