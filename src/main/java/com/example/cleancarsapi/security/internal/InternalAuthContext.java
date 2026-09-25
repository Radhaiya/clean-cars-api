package com.example.cleancarsapi.security.internal;

import com.example.cleancarsapi.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static accessor for the current {@link InternalConsoleUser}, mirroring
 * {@link com.example.cleancarsapi.security.AuthContext}. No roles and no org
 * scoping exist on the console — only the identity. Tenant tokens never land
 * here (different issuer/decoder), and internal tokens never land in
 * {@link AuthContext}.
 */
public final class InternalAuthContext {

    private InternalAuthContext() {
    }

    public static InternalConsoleUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        if (auth.getPrincipal() instanceof InternalConsoleUser user) {
            return user;
        }
        throw new IllegalStateException("Unexpected principal type: " + auth.getPrincipal());
    }
}
