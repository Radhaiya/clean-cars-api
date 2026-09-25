package com.example.cleancarsapi.security.internal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps the validated internal-console {@link Jwt} onto an
 * {@link InternalConsoleUser}: {@code sub} = Firebase uid (not a UUID),
 * {@code email} = the whitelisted address the token was issued for.
 */
@Component
public class InternalConsoleUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        InternalConsoleUser principal = new InternalConsoleUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"));
        return new InternalConsoleUserAuthentication(principal, jwt);
    }
}
