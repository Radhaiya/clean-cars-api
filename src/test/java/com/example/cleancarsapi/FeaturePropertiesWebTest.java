package com.example.cleancarsapi;

import com.example.cleancarsapi.security.internal.InternalConsoleUser;
import com.example.cleancarsapi.service.internal.InternalJwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Migration 013 seeds both switches off; the console flips them; the public
 * read serves {@code Client.*}; maintenance mode turns tenant {@code /api/**}
 * into 503 while the properties read stays open.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeaturePropertiesWebTest {

    static final String MAINTENANCE = "Client.Maintenance.Mode.Enable";
    static final String CALLER = "radhaiya.solutions@gmail.com";

    @Autowired WebApplicationContext context;
    @Autowired InternalJwtService internalJwtService;
    @Autowired JwtEncoder tenantJwtEncoder;
    @Autowired Environment env;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterEach
    void switchMaintenanceOff() throws Exception {
        setValue(MAINTENANCE, "false");
    }

    @Test
    void publicReadServesSeededSwitchesWithoutAuth() throws Exception {
        mvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Client.Maintenance.Mode.Enable']").value(false))
                .andExpect(jsonPath("$['Client.New.Logins.Disabled']").value(false));
    }

    @Test
    void consoleCanFlipSwitchAndKeepsTypeGuard() throws Exception {
        setValue(MAINTENANCE, "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(true))
                .andExpect(jsonPath("$.updatedByEmail").value(CALLER));
        mvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$['Client.Maintenance.Mode.Enable']").value(true));

        setValue(MAINTENANCE, "\"yes\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("property_type_mismatch"));
    }

    @Test
    void maintenanceModeAnswers503OnTenantApiButNotOnProperties() throws Exception {
        setValue(MAINTENANCE, "true").andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tenantToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("maintenance_mode"));
        mvc.perform(get("/api/properties")).andExpect(status().isOk());
    }

    @Test
    void tenantTokenCannotWriteProperties() throws Exception {
        mvc.perform(put("/internal/api/properties/" + MAINTENANCE)
                        .header("Authorization", "Bearer " + tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isUnauthorized());
    }

    ResultActions setValue(String key, String jsonValue) throws Exception {
        return mvc.perform(put("/internal/api/properties/" + key)
                .header("Authorization", "Bearer " + internalJwtService.issueToken(
                        new InternalConsoleUser("fb-props-test", CALLER, CALLER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":" + jsonValue + "}"));
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
                        .claim("email", "props-test@example.com")
                        .claim("name", "props-test@example.com")
                        .build())).getTokenValue();
    }
}
