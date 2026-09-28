package com.example.cleancarsapi.dto;

import java.math.BigDecimal;

/**
 * One employee's performance in kpi-tiles' {@code [from, to]} range —
 * {@code totalServices} is their count of non-{@code CANCELLED} job cards
 * created in range (same population as the {@code TOTAL_SERVICE} chart
 * metric); {@code totalRevenue} is net-of-tax revenue from their <em>paid</em>
 * orders in range (same population as {@code totalRevenue} on {@link
 * KpiTilesResponse}). Only employees (and the unassigned bucket) with at
 * least one such order appear. {@code employeeId}/{@code employeeName} are
 * {@code null}/{@code "Unassigned"} for the row aggregating orders with no
 * assigned employee.
 */
public record EmployeeRevenueResponse(
        String employeeId,
        String employeeName,
        long totalServices,
        BigDecimal totalRevenue
) {
}
