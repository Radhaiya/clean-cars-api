package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body for creating ({@code POST}) or editing ({@code PUT}) an AMC sale's invoice; {@code invoiceDate} defaults to today in the org zone. */
public record AmcInvoiceRequest(
        LocalDate invoiceDate,
        @Size(max = 1000) String notes
) {
}
