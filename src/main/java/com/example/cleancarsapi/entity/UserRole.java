package com.example.cleancarsapi.entity;

/**
 * Roles a user can hold within their organization. Persisted lowercase in the
 * {@code users.role} ENUM column via {@link UserRoleConverter}.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    STAFF;

    public static UserRole fromDb(String value) {
        return UserRole.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
