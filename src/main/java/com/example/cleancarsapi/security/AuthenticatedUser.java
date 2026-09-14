package com.example.cleancarsapi.security;

import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ForbiddenException;

/**
 * The security principal for every authenticated request. Carries everything a
 * service needs for tenant scoping and authorization without another DB hit.
 *
 * @param orgId null until the user has accepted an org invite
 */
public record AuthenticatedUser(long userId, Long orgId, UserRole role, String email, String name) {

    public boolean hasOrg() {
        return orgId != null;
    }

    /** org id, or 403 if the user isn't attached to an organization yet. */
    public long requireOrgId() {
        if (orgId == null) {
            throw new ForbiddenException("User is not attached to an organization");
        }
        return orgId;
    }

    public boolean hasRole(UserRole other) {
        return role == other;
    }
}
