package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/subscription/change-plan}: the Razorpay Plan ID to move to
 * (from {@code GET /api/plans} pricing). Razorpay does the proration for an immediate
 * change: upgrade = charge only the remaining amount, downgrade = refund the difference.
 */
public record ChangePlanRequest(
        @NotBlank String razorpayPlanId
) {
}
