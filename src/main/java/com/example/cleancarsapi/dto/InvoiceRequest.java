package com.example.cleancarsapi.dto;

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
        @PositiveOrZero Integer nextServiceKm,
        @Size(max = 1000) String notes
) {
}
