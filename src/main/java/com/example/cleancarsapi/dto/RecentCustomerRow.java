package com.example.cleancarsapi.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Query projection — one customer's most recent non-cancelled service-order
 * timestamp, for the dashboard's "recent customers" ranking. Not returned to
 * clients as-is.
 */
public record RecentCustomerRow(UUID customerId, LocalDateTime lastServiceAt) {
}
