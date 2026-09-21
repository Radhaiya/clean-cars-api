package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import java.util.UUID;
/**
 * Acceptance of a plan change. The local row's plan/cycle/status flip when Razorpay's
 * {@code subscription.updated} webhook lands (authoritative); until then the org keeps
 * running on the old plan. Proration (charge the remaining amount / refund the
 * difference) is Razorpay's job — nothing here computes money.
 */
public record ChangePlanResponse(
        UUID subscriptionId,
        SubscriptionStatus status,
        String oldPlanName,
        String newPlanName,
        String newBillingCycle,
        String newRazorpayPlanId,
        String razorpayKeyId
) {
    public static ChangePlanResponse of(
            Subscription s, SubscriptionPlan oldPlan, SubscriptionPlan newPlan,
            String newBillingCycle, String newRazorpayPlanId, String razorpayKeyId) {
        return new ChangePlanResponse(
                s.getId(),
                s.getStatus(),
                oldPlan.getName(),
                newPlan.getName(),
                newBillingCycle,
                newRazorpayPlanId,
                razorpayKeyId);
    }
}
