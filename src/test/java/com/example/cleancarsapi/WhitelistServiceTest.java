package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.internal.AllowedEmail;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.internal.AllowedEmailRepository;
import com.example.cleancarsapi.security.internal.InternalConsoleUser;
import com.example.cleancarsapi.service.internal.WhitelistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The console whitelist rules: the whitelist is the single source of truth for
 * "can log in"; any whitelisted user may add/remove another but never their own
 * email. Emails normalize to lowercase before storage and comparison.
 */
class WhitelistServiceTest {

    AllowedEmailRepository repo;
    WhitelistService service;

    @BeforeEach
    void setup() {
        repo = mock(AllowedEmailRepository.class);
        service = new WhitelistService(repo);
        authenticate("admin-console-test@example.com");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    void authenticate(String email) {
        var principal = new InternalConsoleUser("fb-x", email, email);
        SecurityContextHolder.getContext().setAuthentication(
                new com.example.cleancarsapi.security.internal.InternalConsoleUserAuthentication(
                        principal, Jwt.withTokenValue("t").header("alg", "none").issuer("test")
                                .issuedAt(java.time.Instant.now()).build()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    void rejectsWithCodeWhenEmailNull() {
        var ex = assertThrows(ForbiddenException.class, () -> service.requireWhitelisted(null));
        assertEquals(WhitelistService.NOT_WHITELISTED, ex.getMessage());
    }

    @Test
    void lookupsMatchLowercased() {
        when(repo.findByEmail("someone@x.com")).thenReturn(Optional.of(new AllowedEmail()));
        service.requireWhitelisted("  SOMEONE@X.com ");
        verify(repo).findByEmail("someone@x.com");
    }

    @Test
    void rejectsUnknownEmail() {
        when(repo.findByEmail(any())).thenReturn(Optional.empty());
        var ex = assertThrows(ForbiddenException.class, () -> service.requireWhitelisted("stranger@x.com"));
        assertEquals(WhitelistService.NOT_WHITELISTED, ex.getMessage());
    }

    @Test
    void addNormalizesAndRecordsWhoAdded() {
        when(repo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AllowedEmail saved = service.add("  NEW@Example.COM ");
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("admin-console-test@example.com", saved.getCreatedByEmail());
    }

    @Test
    void addRejectsDuplicate() {
        when(repo.findByEmail("dup@example.com")).thenReturn(Optional.of(new AllowedEmail()));
        var ex = assertThrows(ConflictException.class, () -> service.add("dup@example.com"));
        assertEquals("email_already_whitelisted", ex.getCode());
    }

    @Test
    void removeDeletesAnotherUsersRow() {
        AllowedEmail other = new AllowedEmail();
        other.setId(UUID.randomUUID());
        other.setEmail("colleague@example.com");
        when(repo.findById(other.getId())).thenReturn(Optional.of(other));

        service.remove(other.getId());
        verify(repo).delete(other);
    }

    @Test
    void removeRejectsOwnEmail() {
        AllowedEmail own = new AllowedEmail();
        own.setId(UUID.randomUUID());
        own.setEmail("admin-console-test@example.com"); // the caller's own row
        when(repo.findById(own.getId())).thenReturn(Optional.of(own));

        var ex = assertThrows(ConflictException.class, () -> service.remove(own.getId()));
        assertEquals("cannot_remove_self", ex.getCode());
        verify(repo, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void removeUnknownId() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.remove(UUID.randomUUID()));
    }
}
