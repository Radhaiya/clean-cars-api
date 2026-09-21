package com.example.cleancarsapi.security;

import com.example.cleancarsapi.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Maps the validated {@link Jwt} onto an {@link AuthenticatedUserToken}, pulling
 * {@code sub}, {@code org_id} and {@code role} out of the claims. Ids are UUIDs
 * issued by {@code JwtService} at login.
 */
@Component
public class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        Object rawOrgId = jwt.getClaim("org_id");
        UUID orgId = (rawOrgId instanceof String s) ? UUID.fromString(s) : null;

        UserRole role = UserRole.fromDb(jwt.getClaimAsString("role"));

        AuthenticatedUser principal = new AuthenticatedUser(
                userId, orgId, role, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return new AuthenticatedUserToken(principal, jwt, authorities);
    }
}
