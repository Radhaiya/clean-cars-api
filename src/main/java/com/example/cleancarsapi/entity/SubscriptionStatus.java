package com.example.cleancarsapi.entity;

public enum SubscriptionStatus {
    TRIALING,
    PENDING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED,
    EXPIRED;

    public static SubscriptionStatus fromDb(String value) {
        return SubscriptionStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
