package com.example.cleancarsapi.entity;

/** Billing cadence of a plan, persisted lowercase in {@code subscription_plans.billing_cycle} via {@link BillingCycleConverter}. */
public enum BillingCycle {
    MONTHLY,
    YEARLY;

    public static BillingCycle fromDb(String value) {
        return BillingCycle.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
