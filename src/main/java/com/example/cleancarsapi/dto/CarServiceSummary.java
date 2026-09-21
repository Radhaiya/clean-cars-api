package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * One past service order for a car — enough to list it and drill in by {@code id}.
 * {@code totalAmount} is the computed gross total (sum of line grosses, incl. GST).
 */
public record CarServiceSummary(
        UUID id,
        BigDecimal totalAmount,
        boolean paid,
        ServiceOrderStatus status,
        UUID employeeId,
        String employeeName,
        LocalDateTime serviceDate
) {
}
