package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.LoginResponse;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates login / refresh / logout. Each step delegates to an already
 * transactional collaborator, so this class holds no transaction of its own
 * (which also keeps the refresh-reuse lockout from being rolled back).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final FirebaseIdTokenService firebaseIdTokenService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Verify a Firebase ID token (Google / Apple / phone OTP — Firebase is the only
     * front door) and log the user in. Resolution order: existing {@code firebase_uid}
     * first; failing that, an existing row by email (one-time bridge for accounts
     * created before Firebase was wired up, backfilling {@code firebase_uid} onto it);
     * otherwise a brand-new, org-less account is provisioned.
     */
    public LoginResponse loginWithFirebase(String idToken) {
        FirebaseIdTokenService.FirebaseIdentity identity = firebaseIdTokenService.verify(idToken);

        User user = users.findByFirebaseUid(identity.uid())
                .or(() -> findByEmailAndLink(identity))
                .orElseGet(() -> users.save(User.provisionFromFirebase(
                        identity.uid(), identity.email(), identity.phoneNumber(), identity.name())));

        requireActive(user);

        String refreshToken = refreshTokenService.issue(user.getId());
        return tokens(user, refreshToken);
    }

    private Optional<User> findByEmailAndLink(FirebaseIdTokenService.FirebaseIdentity identity) {
        if (identity.email() == null) {
            return Optional.empty();
        }
        return users.findByEmail(identity.email()).map(user -> {
            user.linkFirebaseUid(identity.uid());
            return users.save(user);
        });
    }

    /** Exchange a valid refresh token for a new access token; rotates the refresh token. */
    public LoginResponse refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);

        User user = users.findById(rotated.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        requireActive(user);

        return tokens(user, rotated.rawToken());
    }

    /** Revoke a refresh token. Idempotent. */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new DisabledException("User account is not active");
        }
    }

    private LoginResponse tokens(User user, String refreshToken) {
        return LoginResponse.bearer(jwtService.issueToken(user), jwtService.ttlSeconds(), refreshToken);
    }
}
