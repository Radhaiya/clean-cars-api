package com.example.cleancarsapi.dto;

import java.util.UUID;

/**
 * Redeem the AMC's current-period bundle as a service order. There are no lines, prices or payment
 * here on purpose — the bundle comes from the AMC's own snapshot at a 100% discount. The vehicle is
 * the AMC's vehicle.
 */
public record AmcRedeemRequest(
        UUID employeeId,
        Integer odometerReading,
        UUID vendorId,
        String notes
) {
}
