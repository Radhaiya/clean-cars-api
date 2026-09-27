package com.example.cleancarsapi.exception;

/**
 * A Twilio API call failed (network error, non-2xx from Twilio, or Twilio rejected
 * the request). Surfaced to callers as 502 Bad Gateway — Twilio is a downstream
 * dependency, not a fault in this API or the caller's request.
 */
public class TwilioApiException extends RuntimeException {

    public TwilioApiException(String message) {
        super(message);
    }

    public TwilioApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
