package com.example.cleancarsapi.security.internal;

import com.example.cleancarsapi.service.internal.WhitelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps the validated internal-console {@link Jwt} onto an
 * {@link InternalConsoleUser}: {@code sub} = Firebase uid (not a UUID),
 * {@code email} = the whitelisted address the token was issued for.
 *
 * <p>The whitelist is re-checked on every request, so removing an email locks
 * that user out immediately instead of when their access token expires (401).
 */
@Component
@RequiredArgsConstructor
public class InternalConsoleUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final WhitelistService whitelistService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!whitelistService.isWhitelisted(jwt.getClaimAsString("email"))) {
            throw new BadCredentialsException("Email is no longer whitelisted");
        }
        InternalConsoleUser principal = new InternalConsoleUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"));
        return new InternalConsoleUserAuthentication(principal, jwt);
    }
}
