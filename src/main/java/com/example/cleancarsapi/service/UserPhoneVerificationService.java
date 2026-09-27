package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.UserPhoneCheckRequest;
import com.example.cleancarsapi.dto.UserPhoneStartRequest;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Twilio Verify phone verification of the logged-in user's OWN number (SMS or
 * WhatsApp — the caller picks per request). Buying a paid plan requires a
 * verified phone ({@code SubscriptionService.subscribe}, 409
 * {@code phone_verification_required}); the trial does not. A single workflow
 * class — not a CRUD resource, so not the CRUD-four split (docs/ARCHITECTURE.md).
 * <p>Twilio hosts the OTP state (code, expiry, attempt limits); the only thing
 * stored here is the outcome: an approved check persists the verified number
 * onto {@code users.phone} and flips {@code phone_verified}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserPhoneVerificationService {

    private final UserRepository users;
    private final TwilioGateway twilio;

    /** Send a fresh OTP to the requested phone over the requested channel. */
    public void start(UserPhoneStartRequest request) {
        twilio.startVerification(request.phone(), request.channel().toLowerCase());
    }

    /**
     * Check the OTP the user typed. On Twilio's {@code approved} the given number
     * becomes the account's phone — persisted alongside the verified flag (the
     * verified number wins over any stale stored one, and Google/Apple accounts
     * may not have had a phone at all). Wrong code / nothing in flight → 400;
     * Twilio's own rate limits cover brute force.
     */
    @Transactional
    public void check(UserPhoneCheckRequest request) {
        TwilioGateway.CheckOutcome outcome = twilio.checkVerification(request.phone(), request.code());

        switch (outcome) {
            case APPROVED -> {
                User user = users.findById(AuthContext.require().userId())
                        .orElseThrow(() -> new NotFoundException("user", AuthContext.require().userId()));
                user.applyVerifiedPhone(request.phone());
                users.save(user);
                log.info("User {} phone verified (ending {})", user.getId(), tail(request.phone()));
            }
            case PENDING, NO_PENDING_VERIFICATION ->
                    throw new BadRequestException("Incorrect or expired verification code");
        }
    }

    private String tail(String e164Phone) {
        return e164Phone.length() <= 4 ? e164Phone : e164Phone.substring(e164Phone.length() - 4);
    }
}
