package com.example.cleancarsapi.exception;

/**
 * A webhook references a subscription this API doesn't know (arrived before the
 * local row committed, or a foreign environment's data). Answered non-200 so
 * Razorpay's at-least-once delivery retries later.
 */
public class RazorpayWebhookException extends RuntimeException {

    public RazorpayWebhookException(String message) {
        super(message);
    }
}
