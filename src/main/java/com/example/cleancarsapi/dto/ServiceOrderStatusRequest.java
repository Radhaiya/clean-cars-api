package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderStatus;
import jakarta.validation.constraints.NotNull;

/** Quick-edit body for {@code PATCH /api/service-orders/{id}/status}. */
public record ServiceOrderStatusRequest(@NotNull ServiceOrderStatus status) {
}
