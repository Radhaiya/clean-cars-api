package com.example.cleancarsapi.security;

import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Static accessor for the current {@link AuthenticatedUser}. Use it anywhere in
 * the request thread — controllers, services, repositories — to get the caller's
 * user id, org id and role without threading a parameter through every method.
 */
public final class AuthContext {

    private AuthContext() {
    }

    public static AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("Unexpected principal type: " + auth.getPrincipal());
    }

    public static AuthenticatedUser require(UserRole role) {
        AuthenticatedUser user = require();
        if (!user.hasRole(role)) {
            throw new ForbiddenException("Access denied: requires role " + role);
        }
        return user;
    }

    /** Convenience for the very common "which tenant is this?" lookup. */
    public static UUID requireOrgId() {
        return require().requireOrgId();
    }
}
