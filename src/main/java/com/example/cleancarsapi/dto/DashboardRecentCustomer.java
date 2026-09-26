package com.example.cleancarsapi.dto;

import java.time.LocalDateTime;

/**
 * One of the dashboard's recently served customers — the customer plus the
 * timestamp of their most recent service order (cancelled jobs don't count as
 * "served").
 */
public record DashboardRecentCustomer(
        String id,
        String name,
        String phone,
        LocalDateTime lastServiceAt
) {
}
