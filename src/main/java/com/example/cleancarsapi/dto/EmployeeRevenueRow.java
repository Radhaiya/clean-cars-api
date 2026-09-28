package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Query projection — one paid order-item line keyed by the order's assigned
 * employee, for kpi-tiles' revenue-by-employee breakdown. Internal — not
 * returned to clients as-is.
 */
public record EmployeeRevenueRow(
        UUID employeeId,
        BigDecimal basePrice,
        BigDecimal taxPercentage,
        boolean taxIncluded,
        int quantity
) {
}
