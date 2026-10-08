package com.example.cleancarsapi.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One ACTIVE AMC ending within the renewal window — a row of the "expiring soon" list. {@code vehicleKind} is
 * {@code CAR} / {@code BIKE}; {@code ownerPhone} lets the garage call for the renewal. {@code remaining} is the
 * visits still unused (current + future slots).
 */
public record AmcExpiringResponse(
        UUID subscriptionId,
        String vehicleKind,
        String vehicleNumber,
        String ownerName,
        String ownerPhone,
        String planName,
        LocalDate endDate,
        long daysLeft,
        int remaining
) {
}
