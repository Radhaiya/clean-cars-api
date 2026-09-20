package com.example.cleancarsapi.service;

/**
 * Signal thrown when a concurrent delivery of the same webhook event won the
 * {@code payment_events.razorpay_event_id} unique-key race: another transaction
 * is processing it right now. The controller must answer HTTP 200 in both cases —
 * "I processed it" and "it's already processed".
 */
public class DuplicateWebhookException extends RuntimeException {

    DuplicateWebhookException(String eventId) {
        super("Webhook " + eventId + " is already being processed / processed");
    }
}
