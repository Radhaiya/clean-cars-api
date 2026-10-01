package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sell an AMC to ONE vehicle (exactly one of {@code carId} / {@code bikeId}) from a plan variant.
 * {@code rows}, when given, override the variant's per-service price / tax for this sale (a
 * snapshot — every field editable, but the services must be exactly the plan's). Payment is full,
 * upfront; {@code startDate} / {@code paymentDate} default to today (org timezone) and a start date
 * in the past is rejected.
 */
public record AmcSaleRequest(
        UUID carId,
        UUID bikeId,
        @NotNull UUID variantId,
        LocalDate startDate,
        @Valid List<AmcVariantRequest.Row> rows,
        UUID sellerEmployeeId,
        @NotNull PaymentType paymentType,
        LocalDate paymentDate
) {
}
