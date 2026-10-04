package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Email verification of the logged-in user's OWN address (the sign-in email) via a
 * 6-digit OTP sent through Brevo. Unlike phone (Twilio hosts the OTP state), the
 * code state lives on the {@code users} row: SHA-256 hash, expiry, send time (resend
 * cooldown) and wrong-attempt count. A single workflow class, not CRUD.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserEmailVerificationService {

    static final int VALID_MINUTES = 10;
    static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final BrevoGateway brevo;

    /** Generate a fresh code, store its hash and email it. Mail goes out only after the row is saved. */
    @Transactional
    public void start() {
        User user = current();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("This account has no email address");
        }
        if (user.isEmailVerified()) {
            throw new ConflictException("email_already_verified", "Email is already verified");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getEmailOtpSentAt() != null
                && user.getEmailOtpSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new ConflictException("email_otp_cooldown", "Please wait a minute before requesting another code");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.issueEmailOtp(hash(user.getId(), code), now, now.plusMinutes(VALID_MINUTES));
        users.save(user);
        brevo.sendVerificationCode(user.getEmail(), user.getName(), code, VALID_MINUTES);
    }

    /** Check the typed code; wrong/expired → 400, too many wrong tries burns the code. */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void check(String code) {
        User user = current();
        if (user.isEmailVerified()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getEmailOtpHash() == null || user.getEmailOtpExpiresAt() == null
                || user.getEmailOtpExpiresAt().isBefore(now)
                || user.getEmailOtpAttempts() >= MAX_ATTEMPTS) {
            throw new BadRequestException("Incorrect or expired verification code");
        }
        if (!MessageDigest.isEqual(
                hash(user.getId(), code).getBytes(StandardCharsets.UTF_8),
                user.getEmailOtpHash().getBytes(StandardCharsets.UTF_8))) {
            user.recordFailedEmailOtp();
            users.save(user);
            throw new BadRequestException("Incorrect or expired verification code");
        }
        user.markEmailVerified();
        users.save(user);
        log.info("User {} email verified", user.getId());
    }

    private User current() {
        UUID id = AuthContext.require().userId();
        return users.findById(id).orElseThrow(() -> new NotFoundException("user", id));
    }

    private static String hash(UUID userId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
