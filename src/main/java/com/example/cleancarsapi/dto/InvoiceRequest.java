package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body for creating ({@code POST}) or editing ({@code PUT}) an order's invoice. Everything is
 * optional: {@code invoiceDate} defaults to today in the org's timezone on create (unchanged on
 * update when omitted); the next-service hints and notes clear when omitted.
 */
public record InvoiceRequest(
        LocalDate invoiceDate,
        LocalDate nextServiceDate,
        @PositiveOrZero @Max(DistanceLimits.MAX_METERS) Integer nextServiceKm,
        @Size(max = FieldLimits.NOTES_MAX) String notes
) {
}
