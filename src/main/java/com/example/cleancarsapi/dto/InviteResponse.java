package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.InviteStatus;
import com.example.cleancarsapi.entity.OrgInvite;
import com.example.cleancarsapi.entity.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An {@code org_invites} row as JSON. Serves both sides:
 * <ul>
 *   <li>owner view (GET /api/invites): {@code orgName} stays null (caller context).</li>
 *   <li>invitee view (GET /api/invites/me): {@code orgName} and {@code invitedByName}
 *       are populated so the UI can show <em>who</em> is inviting, plus
 *       {@code employeeName} (the role slot the owner opened for them).</li>
 * </ul>
 * {@code status} (PENDING / ACCEPTED / EXPIRED / REVOKED / DECLINED) and
 * {@code role} (MANAGER / WORKER) are uppercase enum names.
 */
public record InviteResponse(
        UUID id,
        String email,
        UserRole role,
        InviteStatus status,
        UUID employeeId,
        String employeeName,
        String orgName,
        String invitedByName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt
) {
    public static InviteResponse from(OrgInvite invite, Employee employee,
                                      String orgName, String invitedByName) {
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getRole(),
                invite.getStatus(),
                invite.getEmployeeId(),
                employee != null ? employee.getName() : null,
                orgName,
                invitedByName,
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                invite.getAcceptedAt());
    }
}
