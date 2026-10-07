package com.example.cleancarsapi.dto;

import java.util.UUID;

/** Quick-edit body for {@code PATCH /api/service-orders/{id}/employee}. A null {@code employeeId} unassigns. */
public record ServiceOrderEmployeeRequest(UUID employeeId) {
}
