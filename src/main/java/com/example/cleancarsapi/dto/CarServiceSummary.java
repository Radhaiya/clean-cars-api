package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * One past service order for a car — enough to list it and drill in by {@code id}.
 * {@code totalAmount} is the computed gross total (sum of line grosses, incl. tax);
 * {@code amountPaid} is what has been received so far (less than it on a part-paid split order).
 */
public record CarServiceSummary(
        UUID id,
        BigDecimal totalAmount,
        boolean paid,
        BigDecimal amountPaid,
        ServiceOrderStatus status,
        UUID employeeId,
        String employeeName,
        LocalDateTime serviceDate
) {
}
