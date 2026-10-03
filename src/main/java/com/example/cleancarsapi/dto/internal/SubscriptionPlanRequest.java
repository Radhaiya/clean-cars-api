package com.example.cleancarsapi.dto.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST/PUT /internal/api/plans}. PUT is a full replace: a null number means
 * "unlimited" (and {@code statsRangeYears = 0} hides the statistics page), a blank Razorpay id
 * means "that cycle is not offered". {@code isTrial} is not settable.
 */
public record SubscriptionPlanRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String razorpayMonthlyPlanId,
        @Size(max = 100) String razorpayYearlyPlanId,
        @Min(1) Integer maxUsers,
        @Min(1) Integer maxCars,
        @Min(1) Integer reportWindowMonths,
        @Min(0) Integer statsRangeYears,
        boolean invoiceGeneration,
        boolean amcEnabled,
        boolean isPublic,
        int sortOrder
) {
}
