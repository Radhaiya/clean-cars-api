package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Redeem the AMC's current-period bundle as a service order. There are no lines, prices or payment
 * here on purpose — the bundle comes from the AMC's own snapshot at a 100% discount. The vehicle is
 * the AMC's vehicle.
 */
public record AmcRedeemRequest(
        UUID employeeId,
        @PositiveOrZero @Max(DistanceLimits.MAX_METERS) Integer odometerReading,
        UUID vendorId,
        @Size(max = FieldLimits.NOTES_MAX) String notes
) {
}
