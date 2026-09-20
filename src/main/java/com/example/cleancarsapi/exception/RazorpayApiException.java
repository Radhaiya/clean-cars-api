package com.example.cleancarsapi.exception;

/** Razorpay API call failed (network/error response) or its configuration is missing (HTTP 500 mapping). */
public class RazorpayApiException extends RuntimeException {

    public RazorpayApiException(String message) {
        super(message);
    }

    public RazorpayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
