package com.example.cleancarsapi.entity;

/**
 * How a service order is paid, persisted lowercase in {@code service_orders.payment_plan} via
 * {@link PaymentPlanConverter}. Both plans write to the same {@code payments} ledger:
 * {@code ONE_TIME} allows exactly one payment, always the full order total; {@code SPLIT}
 * allows any number of partial payments that may not exceed the remaining amount.
 */
public enum PaymentPlan {
    ONE_TIME,
    SPLIT;

    public static PaymentPlan fromDb(String value) {
        return PaymentPlan.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
