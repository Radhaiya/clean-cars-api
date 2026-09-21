package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * API timestamps are served in the caller's org timezone (wall-time ISO string,
 * no offset — same shape as before), UTC when there is no org on the call.
 * The test inserts its own org (Asia/Kolkata) — there is no seed data.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimezoneSerializationTest {

    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;

    UUID orgId;

    record Sample(LocalDateTime createdAt) {
    }

    @BeforeEach
    void insertOrg() {
        orgId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name, timezone) VALUES (?, ?, ?)",
                toBytes(orgId), "tz-test-org", "Asia/Kolkata");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(orgId));
    }

    @Test
    void convertsToCallerOrgTimezone() {
        authenticateAsOrg(orgId);
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

    private void authenticateAsOrg(UUID orgId) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), orgId, UserRole.OWNER, "test", "test");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    /** BINARY(16) columns take the raw 16-byte UUID form. */
    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
