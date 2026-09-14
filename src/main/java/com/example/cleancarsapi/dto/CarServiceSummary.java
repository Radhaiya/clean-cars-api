package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One past service order for a car — enough to list it and drill in by {@code id}.
 * {@code totalAmount} is the computed gross total (sum of line grosses, incl. GST).
 */
public record CarServiceSummary(
        Long id,
        BigDecimal totalAmount,
        boolean paid,
        ServiceOrderStatus status,
        Long employeeId,
        String employeeName,
        LocalDateTime serviceDate
) {
}
