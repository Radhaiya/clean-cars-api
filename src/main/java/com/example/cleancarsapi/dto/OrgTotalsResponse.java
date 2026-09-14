package com.example.cleancarsapi.dto;

/**
 * All-time org counts — no date range. {@code totalServices} is the service
 * *catalog* size (how many services the org offers), not a count of job cards.
 * Not gated by any plan limit — a basic count, not "statistics history".
 */
public record OrgTotalsResponse(
        long totalCars,
        long totalServices,
        long totalEmployees,
        long totalCustomers
) {
}
