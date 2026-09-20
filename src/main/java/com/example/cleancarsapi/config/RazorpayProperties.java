package com.example.cleancarsapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.razorpay.*} — Razorpay API credentials and the webhook signing secret.
 * Locally test-mode keys from the Razorpay Dashboard; stage/prod read the
 * {@code RAZORPAY_KEY_ID} / {@code RAZORPAY_KEY_SECRET} / {@code RAZORPAY_WEBHOOK_SECRET}
 * env vars (fail-closed like JWT_SECRET when unset — property binding fails on a null).
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret) {
}
