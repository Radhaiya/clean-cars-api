package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One payment on an order. {@code remainingAfter} is the running balance — the order's gross
 * total minus this payment and every one before it (by date, then record time), clamped at
 * zero — so the UI can show what was still owed after each split.
 */
public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        PaymentType paymentType,
        LocalDate paymentDate,
        BigDecimal remainingAfter,
        LocalDateTime createdAt
) {
    /** {@code payments} must already be in chronological order. */
    public static List<PaymentResponse> listOf(List<Payment> payments, BigDecimal grossTotal) {
        List<PaymentResponse> result = new ArrayList<>(payments.size());
        BigDecimal remaining = grossTotal;
        for (Payment p : payments) {
            remaining = remaining.subtract(p.getAmount());
            result.add(new PaymentResponse(p.getId(), p.getAmount(), p.getPaymentType(), p.getPaymentDate(),
                    remaining.max(BigDecimal.ZERO), p.getCreatedAt()));
        }
        return result;
    }
}
