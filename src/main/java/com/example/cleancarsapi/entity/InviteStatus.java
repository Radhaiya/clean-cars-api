package com.example.cleancarsapi.entity;

/**
 * Lifecycle of an {@code org_invites} row. Persisted lowercase in the
 * {@code org_invites.status} ENUM column via {@link InviteStatusConverter}.
 * <p>{@code DECLINED} is the invitee's own deny; {@code REVOKED} is the
 * sender's cancel. Both leave the person re-invitable (a fresh invite row
 * is a fresh decision) — see docs/FEATURE-INVITES.md.
 */
public enum InviteStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED,
    DECLINED;

    public static InviteStatus fromDb(String value) {
        return InviteStatus.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
