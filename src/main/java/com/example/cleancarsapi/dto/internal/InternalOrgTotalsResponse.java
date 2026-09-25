package com.example.cleancarsapi.dto.internal;

import java.util.UUID;

/** All-time per-org counts — {@code GET /internal/api/orgs/{id}/stats}, embedded on the click-through too. */
public record InternalOrgTotalsResponse(
        long totalCars,
        long totalBikes,
        long totalCustomers,
        long totalEmployees,
        long totalServiceOrders,
        long totalServiceCatalog,
        long totalExpenses
) {
}
