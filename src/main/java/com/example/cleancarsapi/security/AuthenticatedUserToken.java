package com.example.cleancarsapi.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Authentication whose principal is an {@link AuthenticatedUser} (not the raw
 * {@link Jwt}), so {@code AuthContext.require()} can hand callers a typed user.
 */
public class AuthenticatedUserToken extends AbstractAuthenticationToken {

    private final transient AuthenticatedUser principal;
    private final transient Jwt token;

    public AuthenticatedUserToken(AuthenticatedUser principal, Jwt token,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return principal.email();
    }
}
