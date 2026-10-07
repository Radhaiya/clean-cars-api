package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.AmcInvoice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An AMC sale's invoice plus the bill-to snapshot the AMC itself lacks: the AMC belongs to a vehicle,
 * so the customer is the vehicle's owner. The plan, rows and sale totals come from {@code GET /api/amc-subscriptions/{id}}.
 */
public record AmcInvoiceResponse(
        UUID id,
        UUID amcSubscriptionId,
        int invoiceNumber,
        LocalDate invoiceDate,
        String notes,
        String customerName,
        String customerPhone,
        ServiceOrderVehicle vehicle,
        LocalDateTime createdAt
) {
    public static AmcInvoiceResponse of(AmcInvoice i, String customerName, String customerPhone, ServiceOrderVehicle vehicle) {
        return new AmcInvoiceResponse(i.getId(), i.getAmcSubscriptionId(), i.getInvoiceNumber(), i.getInvoiceDate(),
                i.getNotes(), customerName, customerPhone, vehicle, i.getCreatedAt());
    }
}
