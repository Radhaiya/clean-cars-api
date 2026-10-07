package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Invoice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** An order's invoice — just the invoice-only fields; the client combines it with the order and the org. */
public record InvoiceResponse(
        UUID id,
        UUID serviceOrderId,
        int invoiceNumber,
        LocalDate invoiceDate,
        LocalDate nextServiceDate,
        Integer nextServiceKm,
        String notes,
        LocalDateTime createdAt
) {
    public static InvoiceResponse of(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getServiceOrderId(), i.getInvoiceNumber(), i.getInvoiceDate(),
                i.getNextServiceDate(), i.getNextServiceKm(), i.getNotes(), i.getCreatedAt());
    }
}
