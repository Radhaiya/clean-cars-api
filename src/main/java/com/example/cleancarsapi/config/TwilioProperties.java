package com.example.cleancarsapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.twilio.*} — Twilio credentials for customer phone-number OTP
 * (Twilio Verify): the API account, an auth token, and the Verify service that
 * has both the SMS and WhatsApp channels enabled. Blank values = Twilio is not
 * configured; the gateway refuses to send in that case.
 */
@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(String accountSid, String authToken, String verifyServiceSid) {
}
