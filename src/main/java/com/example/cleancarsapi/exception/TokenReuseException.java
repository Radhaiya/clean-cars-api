package com.example.cleancarsapi.exception;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * A revoked refresh token was replayed. Distinct type so the rotation transaction
 * can commit its "revoke all sessions" write ({@code noRollbackFor}) before the
 * 401 propagates.
 */
public class TokenReuseException extends BadCredentialsException {

    public TokenReuseException(String message) {
        super(message);
    }
}
