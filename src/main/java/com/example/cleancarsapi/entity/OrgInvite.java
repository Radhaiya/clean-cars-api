package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code org_invites} row — an owner's invitation to join their org, addressed to
 * one {@code employees} roster row (its {@code email} is the address; see
 * {@code InviteService}). Append-only log: one row per invite attempt; membership
 * lives on {@code users.org_id} / {@code employees.user_id} only. Emails are
 * stored lowercase. There is no email transport yet — the invitee sees their
 * pending invites in-app ({@code GET /api/invites/me}) once they sign in.
 *
 * <p>{@code token} is a random opaque string reserved for the future email link;
 * acceptance today is by invite id. {@code employeeId} survives as NULL if the
 * employee row is deleted later (the log row itself is kept).
 */
@Entity
@Table(name = "org_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrgInvite {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(updatable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Column(nullable = false)
    private InviteStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime acceptedAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isPending() {
        return status == InviteStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public void revoke() {
        this.status = InviteStatus.REVOKED;
    }

    public void decline() {
        this.status = InviteStatus.DECLINED;
    }

    public void accept(LocalDateTime now) {
        this.status = InviteStatus.ACCEPTED;
        this.acceptedAt = now;
    }

    public static OrgInvite create(UUID orgId, UUID invitedBy, UUID employeeId, String email, UserRole role,
                                   String token, LocalDateTime now, LocalDateTime expiresAt) {
        OrgInvite invite = new OrgInvite();
        invite.orgId = orgId;
        invite.invitedBy = invitedBy;
        invite.employeeId = employeeId;
        invite.email = email;
        invite.role = role;
        invite.token = token;
        invite.status = InviteStatus.PENDING;
        invite.expiresAt = expiresAt;
        return invite;
    }
}
