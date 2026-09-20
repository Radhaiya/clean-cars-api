package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * API timestamps are served in the caller's org timezone (wall-time ISO string,
 * no offset — same shape as before), UTC when there is no org on the call.
 * Org 1 is seeded with {@code Asia/Kolkata}.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimezoneSerializationTest {

    @Autowired JsonMapper mapper;

    record Sample(LocalDateTime createdAt) {
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void convertsToCallerOrgTimezone() {
        authenticateAsOrg(1L);
        // 04:29:49 UTC -> 09:59:49 same instant in Asia/Kolkata
        String json = mapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 9, 18, 4, 29, 49)));
        assertEquals("{\"createdAt\":\"2026-09-18T09:59:49\"}", json);
    }

    @Test
    void utcWhenUnauthenticated() {
        String json = mapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 9, 18, 4, 29, 49)));
        assertEquals("{\"createdAt\":\"2026-09-18T04:29:49\"}", json);
    }

    @Test
    void utcWhenCallerHasNoOrg() {
        authenticateAsOrg(null);
        String json = mapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 9, 18, 4, 29, 49)));
        assertEquals("{\"createdAt\":\"2026-09-18T04:29:49\"}", json);
    }

    private void authenticateAsOrg(Long orgId) {
        AuthenticatedUser user = new AuthenticatedUser(1L, orgId, UserRole.OWNER, "test", "test");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
}
