package com.example.cleancarsapi;

import com.example.cleancarsapi.security.internal.InternalConsoleUser;
import com.example.cleancarsapi.service.internal.InternalJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal console's security boundary in one process: {@code /internal/**}
 * speaks only the internal token world (its own secret + issuer), the tenant
 * chain never accepts it, and the whitelist is the only mutable console
 * resource. Chain isolation is proven with tokens minted by the app's own
 * encoders — real validation, not faked authentication.
 *
 * <p>The whitelist (with its one seeded row) comes from the Liquibase
 * migration; {@code cannot_remove_self} is exercised against the seeded
 * caller's own row (its id is discovered by listing).
 */
@SpringBootTest
@ActiveProfiles("test")
class InternalConsoleWebTest {

    static final String CALLER_EMAIL = "web-console-test@example.com";
    static final String SEEDED_EMAIL = "radhaiya.solutions@gmail.com";

    @Autowired WebApplicationContext context;
    @Autowired InternalJwtService internalJwtService;
    @Autowired JwtEncoder tenantJwtEncoder;
    @Autowired Environment env;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupGuestRow() {
        jdbc.update("DELETE FROM allowed_emails WHERE email = 'console-guest@example.com'");
    }

    @Test
    void internalTokenOpensInternalEndpoints() throws Exception {
        mvc.perform(get("/internal/api/allowed-emails").header("Authorization", "Bearer " + internalToken(CALLER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void internalTokenCannotOpenTenantEndpoints() throws Exception {
        // The tenant chain would have to validate the internal issuer — it doesn't know it.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + internalToken(CALLER_EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantTokenCannotOpenHelper() throws Exception {
        mvc.perform(get("/internal/api/allowed-emails").header("Authorization", "Bearer " + tenantToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedInternalRequestIsRejected() throws Exception {
        mvc.perform(get("/internal/api/orgs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededEmailCanAddAndList() throws Exception {
        mvc.perform(post("/internal/api/allowed-emails")
                        .header("Authorization", "Bearer " + internalToken(SEEDED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"CONSOLE-GUEST@example.COM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("console-guest@example.com"))
                .andExpect(jsonPath("$.createdByEmail").value(SEEDED_EMAIL));
    }

    @Test
    void duplicateAddIs409WithCodedConflict() throws Exception {
        mvc.perform(post("/internal/api/allowed-emails")
                        .header("Authorization", "Bearer " + internalToken(SEEDED_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"radhaiya.solutions@gmail.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_whitelisted"));
    }

    @Test
    void cannotRemoveOwnEmail() throws Exception {
        UUID ownId = findSeededRowId();
        mvc.perform(delete("/internal/api/allowed-emails/" + ownId)
                        .header("Authorization", "Bearer " + internalToken(SEEDED_EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cannot_remove_self"));
    }

    private UUID findSeededRowId() throws Exception {
        MvcResult result = mvc.perform(get("/internal/api/allowed-emails")
                        .header("Authorization", "Bearer " + internalToken(SEEDED_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int emailIdx = body.indexOf(SEEDED_EMAIL);
        String before = body.substring(0, emailIdx);
        int idIdx = before.lastIndexOf("\"id\":\"");
        return UUID.fromString(before.substring(idIdx + 6, body.indexOf('"', idIdx + 6)));
    }

    String tenantToken() {
        return tenantJwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(env.getProperty("app.jwt.issuer"))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .subject(UUID.randomUUID().toString())
                        .claim("org_id", UUID.randomUUID().toString())
                        .claim("role", "STAFF")
                        .claim("email", CALLER_EMAIL)
                        .claim("name", CALLER_EMAIL)
                        .build())).getTokenValue();
    }

    String internalToken(String email) {
        return internalJwtService.issueToken(new InternalConsoleUser("fb-web-test", email, email));
    }
}
