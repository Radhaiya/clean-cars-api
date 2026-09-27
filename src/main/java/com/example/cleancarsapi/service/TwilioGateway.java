package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.TwilioProperties;
import com.example.cleancarsapi.exception.TwilioApiException;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.exception.TwilioException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Thin Twilio Verify wrapper (twilio-java 12.x). Twilio Verify hosts the OTP
 * state — start sends the code over the chosen channel, check validates it —
 * so this class holds no codes, no expiry and no attempt counting. Failures of
 * the send/check call itself are "best-effort" at the caller's discretion; the
 * gateway throws {@link TwilioApiException} (502) only for Twilio-side outages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(TwilioProperties.class)
public class TwilioGateway {

    public enum CheckOutcome { APPROVED, PENDING, NO_PENDING_VERIFICATION }

    private final TwilioProperties props;
    private volatile boolean initialized;

    /**
     * POST /verification — send a 6-digit OTP to the E.164 phone over "sms" or
     * "whatsapp" (the channel is validated on the request DTO). Twilio owns the
     * code and its expiry; nothing about the attempt is stored here.
     */
    public void startVerification(String e164Phone, String channel) {
        ensureInitialized();
        try {
            Verification.creator(props.verifyServiceSid(), e164Phone, channel).create();
            log.info("Twilio verification started via {} for phone ending {}", channel, tail(e164Phone));
        } catch (TwilioException e) {
            throw new TwilioApiException("Could not start phone verification (Twilio): " + e.getMessage(), e);
        }
    }

    /**
     * POST /verification/check — review the code the caller's customer typed.
     * APPROVED = the phone is verified; PENDING = wrong code (or not yet
     * approved); NO_PENDING_VERIFICATION = nothing in flight (never started,
     * expired, or already checked). Transport/systemic failures throw
     * {@link TwilioApiException}.
     */
    public CheckOutcome checkVerification(String e164Phone, String code) {
        ensureInitialized();
        try {
            VerificationCheck check = VerificationCheck.creator(props.verifyServiceSid())
                    .setTo(e164Phone)
                    .setCode(code)
                    .create();
            log.info("Twilio verification check for phone ending {}: {}", tail(e164Phone), check.getStatus());
            return "approved".equals(check.getStatus())
                    ? CheckOutcome.APPROVED
                    : CheckOutcome.PENDING;
        } catch (ApiException e) {
            if (e.getStatusCode() == 404) {
                return CheckOutcome.NO_PENDING_VERIFICATION;
            }
            throw new TwilioApiException("Phone verification check failed (Twilio): " + e.getMessage(), e);
        } catch (TwilioException e) {
            throw new TwilioApiException("Phone verification check failed (Twilio): " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() {
        if (!StringUtils.hasText(props.accountSid()) || !StringUtils.hasText(props.authToken())
                || !StringUtils.hasText(props.verifyServiceSid())) {
            throw new TwilioApiException("Twilio is not configured (app.twilio.*)");
        }
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    Twilio.init(props.accountSid(), props.authToken());
                    initialized = true;
                }
            }
        }
    }

    /** Last 4 digits only — never log a full phone number. */
    private String tail(String e164Phone) {
        return e164Phone.length() <= 4 ? e164Phone : e164Phone.substring(e164Phone.length() - 4);
    }
}
