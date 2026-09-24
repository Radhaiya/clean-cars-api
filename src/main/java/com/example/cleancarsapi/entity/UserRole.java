package com.example.cleancarsapi.entity;

/**
 * Roles a user can hold within their organization. Persisted lowercase in the
 * {@code users.role} ENUM column via {@link UserRoleConverter}.
 * <p>{@code MEMBER} roles come from an accepted invite ({@code InviteService});
 * {@code OWNER} is the org creator's role and the only one that can send invites.
 * {@code ADMIN}/{@code STAFF} are legacy values kept until the role model is
 * revisited (migration {@code 004-invite-roles.sql}; see docs/FEATURE-INVITES.md).
 */
public enum UserRole {
    OWNER,
    MANAGER,
    WORKER,
    ADMIN,
    STAFF;

    /** Roles an invite may grant. */
    public static boolean isInvitable(UserRole role) {
        return role == MANAGER || role == WORKER;
    }

    public static UserRole fromDb(String value) {
        return UserRole.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
