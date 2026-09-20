package com.example.cleancarsapi.entity;

/**
 * The billing cycle a buyer picks at checkout — selects which Razorpay Plan ID
 * ({@code subscription_plans.razorpay_monthly_plan_id} / {@code ..._yearly_plan_id})
 * backs the purchase. Not persisted on a row: the chosen cycle's Razorpay plan ID
 * is stored on the subscription via {@code subscriptions.razorpay_subscription_id}.
 */
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
