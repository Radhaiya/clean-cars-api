package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotNull;

/** Quick-edit body for {@code PATCH /api/service-orders/{id}/paid}. */
public record ServiceOrderPaidRequest(@NotNull Boolean paid) {
}
