package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/subscription/subscribe}: the Razorpay Plan ID to buy —
 * the value the client picked from {@code GET /api/plans} pricing ({@code pricing.monthly.razorpayPlanId}
 * or {@code pricing.yearly.razorpayPlanId}). The Razorpay plan implies both the plan
 * AND the billing cycle, so the backend resolves the internal
 * {@code subscription_plans} row by matching whichever razorpay plan-id column holds it.
 */
public record SubscribeRequest(
        @NotBlank String razorpayPlanId
) {
}
