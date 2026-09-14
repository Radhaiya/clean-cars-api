package com.example.cleancarsapi.entity;

/** Lifecycle of a service order, persisted lowercase in {@code service_orders.status} via {@link ServiceOrderStatusConverter}. */
public enum ServiceOrderStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static ServiceOrderStatus fromDb(String value) {
        return ServiceOrderStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
