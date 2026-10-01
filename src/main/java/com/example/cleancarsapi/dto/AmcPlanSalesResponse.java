package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * How an AMC plan has sold: totals overall and per variant, and the sales still in force. {@code active} =
 * started and not ended (vehicles mid-way through it), {@code upcoming} = sold but not started yet,
 * {@code expired} = tenure over; {@code totalSold} is all three. {@code soldGross} is the money collected
 * at sale (gross). {@code currentSales} lists the active + upcoming sales, active first.
 */
public record AmcPlanSalesResponse(
        UUID planId,
        String planName,
        Counts totals,
        List<VariantSales> variants,
        List<CurrentSale> currentSales
) {
    public record Counts(int totalSold, int active, int upcoming, int expired, BigDecimal soldGross) {
    }

    /** {@code variantId} is the template variant the sale was made from (it may since have been archived). */
    public record VariantSales(UUID variantId, int tenureMonths, int intervalMonths, boolean archived, Counts counts) {
    }

    /** A sale still in force, with the vehicle (and its current owner) and the AMC's runtime counts. */
    public record CurrentSale(String vehicleKind, String vehicleNumber, String ownerName,
                              AmcSubscriptionResponse amc) {
    }
}
