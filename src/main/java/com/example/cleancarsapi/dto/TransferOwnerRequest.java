package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of {@code PATCH /api/cars/{id}/owner} and {@code PATCH /api/bikes/{id}/owner}. */
public record TransferOwnerRequest(@NotNull UUID customerId) {
}
