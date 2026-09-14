package com.example.cleancarsapi.entity;

/** Lifecycle of an org's subscription, persisted lowercase in {@code subscriptions.status} via {@link SubscriptionStatusConverter}. */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    EXPIRED;

    public static SubscriptionStatus fromDb(String value) {
        return SubscriptionStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
