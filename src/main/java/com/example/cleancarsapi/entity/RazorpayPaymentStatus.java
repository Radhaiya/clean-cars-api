package com.example.cleancarsapi.entity;

/** Razorpay payment state, persisted lowercase in {@code razorpay_payments.status} via {@link RazorpayPaymentStatusConverter}. */
public enum RazorpayPaymentStatus {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED;

    public static RazorpayPaymentStatus fromDb(String value) {
        return RazorpayPaymentStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
