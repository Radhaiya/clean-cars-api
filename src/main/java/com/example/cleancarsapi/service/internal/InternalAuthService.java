package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.LoginResponse;
import com.example.cleancarsapi.service.FirebaseIdTokenService;
import com.example.cleancarsapi.service.internal.InternalRefreshTokenService.Rotated;
import com.example.cleancarsapi.security.internal.InternalConsoleUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the internal console's login / refresh / logout. Same shape as
 * the tenant {@code AuthService}, but the whitelist — not the users table — is
 * the gateway: a verified Firebase identity whose email is not in
 * {@code allowed_emails} gets 403 {@code email_not_whitelisted} and no tokens.
 * No provisioning, no DB user row, no org. Holds no transaction of its own, so
 * the refresh-reuse lockout cannot be rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalAuthService {

    private final FirebaseIdTokenService firebaseIdTokenService;
    private final WhitelistService whitelistService;
    private final InternalJwtService jwtService;
    private final InternalRefreshTokenService refreshTokenService;

    /** Verify the ID token, gate on the whitelist, issue the console token pair. */
    public LoginResponse loginWithFirebase(String idToken) {
        FirebaseIdTokenService.FirebaseIdentity identity = firebaseIdTokenService.verify(idToken);
        whitelistService.requireWhitelisted(identity.email());

        InternalConsoleUser user = new InternalConsoleUser(identity.uid(), normalize(identity.email()), identity.name());
        String refreshToken = refreshTokenService.issue(user.email(), user.uid());
        log.info("Internal console login: {}", user.email());
        return tokens(user, refreshToken);
    }

    /** Refresh: rotate the pair and re-gate on the whitelist — removing an email ends access here. */
    public LoginResponse refresh(String refreshToken) {
        Rotated rotated = refreshTokenService.rotate(refreshToken);
        whitelistService.requireWhitelisted(rotated.email());

        InternalConsoleUser user = new InternalConsoleUser(rotated.firebaseUid(), rotated.email(), null);
        return tokens(user, rotated.rawToken());
    }

    /** Revoke a refresh token. Idempotent. */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase();
    }

    private LoginResponse tokens(InternalConsoleUser user, String refreshToken) {
        return LoginResponse.bearer(jwtService.issueToken(user), jwtService.ttlSeconds(), refreshToken);
    }
}
