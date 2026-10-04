package com.example.cleancarsapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.brevo.*} — Brevo transactional email (account email-verification OTP).
 * Blank {@code apiKey} / {@code senderEmail} = Brevo is not configured; the gateway
 * refuses to send in that case. {@code senderEmail} must be a sender verified in Brevo.
 */
@ConfigurationProperties(prefix = "app.brevo")
public record BrevoProperties(String apiKey, String senderEmail, String senderName) {
}
