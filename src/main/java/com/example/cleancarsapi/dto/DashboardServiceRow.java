package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the dashboard's "today's services" list — a flattened service
 * order: the car/bike split collapses into one {@code vehicleNumber}/
 * {@code vehicleModel} pair, the first line item names the {@code serviceType}
 * and {@code itemCount} carries the rest ("Oil Change +2"), and {@code amount}
 * is the order's gross total.
 */
public record DashboardServiceRow(
        String id,
        String vehicleNumber,
        String vehicleModel,
        String customerId,
        String customerName,
        String customerPhone,
        String serviceType,
        int itemCount,
        String serviceNote,
        ServiceOrderStatus status,
        BigDecimal amount
) {
}
