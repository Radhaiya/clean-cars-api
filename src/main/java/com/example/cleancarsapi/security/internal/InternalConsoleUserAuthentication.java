package com.example.cleancarsapi.security.internal;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Authentication whose principal is an {@link InternalConsoleUser} (not the raw
 * {@link Jwt}), mirroring the tenant-side AuthenticatedUserToken. No granted
 * authorities are meaningful on the console — it is one flat whitelist.
 */
public class InternalConsoleUserAuthentication extends AbstractAuthenticationToken {

    private final transient InternalConsoleUser principal;
    private final transient Jwt token;

    public InternalConsoleUserAuthentication(InternalConsoleUser principal, Jwt token) {
        super(List.of());
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public InternalConsoleUser getPrincipal() {
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

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return super.getAuthorities();
    }
}
