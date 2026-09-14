package com.example.cleancarsapi.security;

import com.example.cleancarsapi.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps the validated {@link Jwt} onto an {@link AuthenticatedUserToken}, pulling
 * {@code sub}, {@code org_id} and {@code role} out of the claims.
 */
@Component
public class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());

        Object rawOrgId = jwt.getClaim("org_id");
        Long orgId = (rawOrgId instanceof Number n) ? n.longValue() : null;

        UserRole role = UserRole.fromDb(jwt.getClaimAsString("role"));

        AuthenticatedUser principal = new AuthenticatedUser(
                userId, orgId, role, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return new AuthenticatedUserToken(principal, jwt, authorities);
    }
}
