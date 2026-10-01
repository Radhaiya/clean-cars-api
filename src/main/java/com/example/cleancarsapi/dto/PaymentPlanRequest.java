package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentPlan;
import jakarta.validation.constraints.NotNull;

/** Quick-edit body for {@code PATCH /api/service-orders/{id}/payment-plan}. */
public record PaymentPlanRequest(@NotNull PaymentPlan paymentPlan) {
}
