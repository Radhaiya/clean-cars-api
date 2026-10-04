package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.BrevoGateway;
import com.example.cleancarsapi.service.UserEmailVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** Brevo email-OTP verification of the account email, with the Brevo gateway mocked. */
@SpringBootTest
@ActiveProfiles("test-api")
class UserEmailVerificationTest {

    @Autowired UserEmailVerificationService service;
    @Autowired UserRepository users;
    @MockitoBean BrevoGateway brevo;

    User user;

    @BeforeEach
    void setup() {
        user = users.save(User.provisionFromFirebase(
                "fb-emailtest-" + UUID.randomUUID(),
                "emailtest-" + UUID.randomUUID() + "@example.com", null, "Email Tester"));
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), null, UserRole.OWNER,
                user.getEmail(), "Email Tester");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        users.deleteById(user.getId());
    }

    private String startAndCaptureCode() {
        service.start();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(brevo).sendVerificationCode(eq(user.getEmail()), any(), code.capture(), anyInt());
        return code.getValue();
    }

    @Test
    void correctCodeMarksEmailVerified() {
        String code = startAndCaptureCode();
        service.check(code);
        assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());
    }

    @Test
    void wrongCodeIsRejectedAndNotVerified() {
        String code = startAndCaptureCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertThrows(BadRequestException.class, () -> service.check(wrong));
        User reloaded = users.findById(user.getId()).orElseThrow();
        assertFalse(reloaded.isEmailVerified());
        assertTrue(reloaded.getEmailOtpAttempts() == 1);
    }

    @Test
    void secondStartWithinCooldownIsRejected() {
        service.start();
        ConflictException ex = assertThrows(ConflictException.class, () -> service.start());
        assertTrue(ex.getCode().equals("email_otp_cooldown"));
    }
}
