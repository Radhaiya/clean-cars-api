package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.RazorpayPayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * One {@code razorpay_payments} row: Razorpay's raw amount comes in paise and
 * is converted to rupee decimals here at the JSON boundary (like PlanService).
 */
public record InternalPaymentResponse(
        UUID id,
        UUID subscriptionId,
        String razorpayPaymentId,
        String razorpayOrderId,
        String razorpayInvoiceId,
        BigDecimal amount,
        String currency,
        String status,
        String paidAt
) {
    public static InternalPaymentResponse from(RazorpayPayment p, String timezone) {
        BigDecimal amountRupees = BigDecimal.valueOf(p.getAmount(), 2).setScale(2, RoundingMode.UNNECESSARY);
        return new InternalPaymentResponse(
                p.getId(),
                p.getSubscriptionId(),
                p.getRazorpayPaymentId(),
                p.getRazorpayOrderId(),
                p.getRazorpayInvoiceId(),
                amountRupees,
                p.getCurrency(),
                p.getStatus().name(),
                ConsoleTimes.inZone(p.getPaidAt(), timezone));
    }
}
