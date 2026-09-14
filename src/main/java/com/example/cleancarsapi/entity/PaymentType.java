package com.example.cleancarsapi.entity;

/** How a service order was paid, persisted lowercase in {@code service_orders.payment_type} via {@link PaymentTypeConverter}. */
public enum PaymentType {
    CARD,
    CASH,
    UPI;

    public static PaymentType fromDb(String value) {
        return PaymentType.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
