package com.example.cleancarsapi.exception;

/**
 * A Brevo API call failed (not configured, network error, or non-2xx from Brevo).
 * Surfaced to callers as 502 Bad Gateway — Brevo is a downstream dependency.
 */
public class BrevoApiException extends RuntimeException {

    public BrevoApiException(String message) {
        super(message);
    }

    public BrevoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
