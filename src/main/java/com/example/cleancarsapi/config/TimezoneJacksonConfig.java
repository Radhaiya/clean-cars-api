package com.example.cleancarsapi.config;

import com.example.cleancarsapi.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
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

    private final OrgTimeZoneResolver orgTimezones;

    @Bean
    JsonMapperBuilderCustomizer orgTimezoneDates() {
        SimpleModule module = new SimpleModule("org-timezone-dates");
        module.addSerializer(LocalDateTime.class, new OrgZoneLocalDateTimeSerializer());
        return builder -> builder.addModule(module);
    }

    /** Explicit registration overrides Jackson 3's built-in java.time serializer. */
    private class OrgZoneLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.atZone(ZoneOffset.UTC).withZoneSameInstant(orgTimezones.zone()).format(ISO));
        }
    }
}
