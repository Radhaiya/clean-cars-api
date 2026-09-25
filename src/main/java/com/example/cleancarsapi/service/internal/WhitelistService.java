package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.entity.internal.AllowedEmail;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.internal.AllowedEmailRepository;
import com.example.cleancarsapi.security.internal.InternalAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The internal console's login whitelist — its only mutable resource. One flat
 * list, no roles: any logged-in whitelisted user may add an email or remove
 * another's (never their own). Codes: {@code email_already_whitelisted},
 * {@code cannot_remove_self}, {@code email_not_whitelisted}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhitelistService {

    /** The machine-readable code sent back when a login is not on the list. */
    public static final String NOT_WHITELISTED = "email_not_whitelisted";

    private final AllowedEmailRepository allowedEmails;

    /** @param email the (verified) login email, matched lowercased and trimmed. */
    public void requireWhitelisted(String email) {
        if (email == null || allowedEmails.findByEmail(normalize(email)).isEmpty()) {
            throw new ForbiddenException(NOT_WHITELISTED);
        }
    }

    @Transactional(readOnly = true)
    public List<AllowedEmail> list() {
        return allowedEmails.findByOrderByCreatedAtDesc();
    }

    @Transactional
    public AllowedEmail add(String email) {
        String normalized = normalize(email);
        if (allowedEmails.findByEmail(normalized).isPresent()) {
            throw new ConflictException("email_already_whitelisted",
                    normalized + " is already whitelisted");
        }
        AllowedEmail row = new AllowedEmail();
        row.setEmail(normalized);
        row.setCreatedByEmail(InternalAuthContext.require().email());
        AllowedEmail saved = allowedEmails.save(row);
        log.info("Whitelisted {} (added by {})", normalized, saved.getCreatedByEmail());
        return saved;
    }

    @Transactional
    public void remove(UUID id) {
        AllowedEmail row = allowedEmails.findById(id)
                .orElseThrow(() -> new NotFoundException("allowed email", id));

        // You cannot remove your own email — a caller must stay logged in and
        // whitelisted for any other management to be possible.
        String callerEmail = InternalAuthContext.require().email();
        if (callerEmail != null && row.getEmail().equals(normalize(callerEmail))) {
            throw new ConflictException("cannot_remove_self",
                    "You cannot remove your own email from the whitelist");
        }

        allowedEmails.delete(row);
        log.info("Removed {} from the whitelist (requested by {})", row.getEmail(), callerEmail);
    }

    /** Console emails are stored lowercase + trimmed; comparison is exact on that form. */
    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase();
    }
}
