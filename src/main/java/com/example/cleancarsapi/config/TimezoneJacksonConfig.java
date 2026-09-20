package com.example.cleancarsapi.config;

import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Serves every API timestamp in the caller's own timezone. The app runs in UTC
 * (see {@code CleanCarsApiApplication}) and the DB stores UTC, so a
 * {@code LocalDateTime} at the JSON boundary is UTC wall time — this converts it
 * to the caller's org zone ({@code organizations.timezone}) before writing it
 * out, as a plain ISO-8601 local string. Unauthenticated calls get UTC.
 */
@Configuration
@RequiredArgsConstructor
public class TimezoneJacksonConfig {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final String ZONE_ATTRIBUTE = "org.timezone";

    private final OrganizationRepository organizations;

    @Bean
    JsonMapperBuilderCustomizer orgTimezoneDates() {
        SimpleModule module = new SimpleModule("org-timezone-dates");
        module.addSerializer(LocalDateTime.class, new OrgZoneLocalDateTimeSerializer());
        return builder -> builder.addModule(module);
    }

    /** The caller's org zone, looked up once per request (memoised); UTC when there is no org. */
    private ZoneId callerZone() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user) || !user.hasOrg()) {
            return ZoneOffset.UTC;
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            Object cached = request.getAttribute(ZONE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof ZoneId zone) {
                return zone;
            }
        }
        ZoneId zone = organizations.findById(user.requireOrgId())
                .map(Organization::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneOffset.UTC);
        if (request != null) {
            request.setAttribute(ZONE_ATTRIBUTE, zone, RequestAttributes.SCOPE_REQUEST);
        }
        return zone;
    }

    /** Explicit registration overrides Jackson 3's built-in java.time serializer. */
    private class OrgZoneLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.atZone(ZoneOffset.UTC).withZoneSameInstant(callerZone()).format(ISO));
        }
    }
}
