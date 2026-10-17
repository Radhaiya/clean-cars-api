package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the org-wide invoice list — a service-order invoice ({@code type} = SERVICE, numbered {@code INV-n})
 * or an AMC-sale invoice ({@code type} = AMC, numbered {@code AMC-n}). Exactly one of {@code serviceOrderId} /
 * {@code amcSubscriptionId} is set; {@code grossTotal} is the live total of the order / sale.
 */
public record InvoiceListItem(
        UUID id,
        String type,
        int invoiceNumber,
        LocalDate invoiceDate,
        UUID serviceOrderId,
        UUID amcSubscriptionId,
        String customerName,
        String customerPhone,
        String vehicleNumber,
        BigDecimal grossTotal,
        LocalDateTime createdAt
) {
}
