package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.UserRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Send an invite: which employee row is being invited ({@code employeeId}) and the
 * role to grant ({@code manager} or {@code worker} only — owners are created by
 * trial start, never by invite). The email address comes from the employee row
 * itself (rejected with a 400 when absent). Enums parse case-insensitively.
 */
public record InviteRequest(
        @NotNull UUID employeeId,
        @NotNull UserRole role
) {
}
