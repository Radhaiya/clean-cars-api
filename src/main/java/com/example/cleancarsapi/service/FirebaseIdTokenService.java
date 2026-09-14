package com.example.cleancarsapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Verifies a Firebase Auth ID token against Firebase's public keys. Firebase is the
 * single front door for every sign-in method (Google, Apple, phone OTP) — whichever
 * provider the client used, the app always ends up with one of these tokens, so this
 * is the only identity verifier the backend needs. See {@link AuthService#loginWithFirebase}.
 */
@Service
public class FirebaseIdTokenService {

    private static final String JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    /**
     * The caller's identity, verified by Firebase. {@code email} / {@code phoneNumber}
     * are each null when the sign-in provider didn't supply one (e.g. phone-only OTP
     * has no email; most Google/Apple sign-ins have no phone).
     */
    public record FirebaseIdentity(String uid, String email, String phoneNumber, String name) {
    }

    private final JwtDecoder decoder;

    public FirebaseIdTokenService(@Value("${app.firebase.project-id}") String projectId) {
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();

        String expectedIssuer = "https://securetoken.google.com/" + projectId;
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<String>(JwtClaimNames.ISS, expectedIssuer::equals);
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(projectId));
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), issuer, audience));

        this.decoder = nimbusDecoder;
    }

    /**
     * @return the verified identity from a valid, unexpired Firebase ID token for this
     *     app's Firebase project.
     * @throws BadCredentialsException the token is malformed, expired, signed by
     *     someone other than Firebase, issued for a different project, or (when an
     *     email is present at all) that email is unverified.
     */
    public FirebaseIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid Firebase ID token", e);
        }

        String uid = jwt.getSubject();
        if (uid == null || uid.isBlank()) {
            throw new BadCredentialsException("Firebase ID token missing subject");
        }

        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaim("email_verified");
        if (email != null && !Boolean.TRUE.equals(emailVerified)) {
            throw new BadCredentialsException("Email is not verified");
        }

        String phoneNumber = jwt.getClaimAsString("phone_number");
        String name = jwt.getClaimAsString("name");
        return new FirebaseIdentity(uid, email, phoneNumber, name);
    }
}
