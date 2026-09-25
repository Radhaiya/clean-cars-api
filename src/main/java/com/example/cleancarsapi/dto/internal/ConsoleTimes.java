package com.example.cleancarsapi.dto.internal;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Console-internal timestamp formatting. The tenant API converts at the JSON
 * boundary via {@code TimezoneJacksonConfig} (caller's org zone); the console
 * sees rows from many orgs in one response, so conversion happens per row in
 * the read services, to the subject org's zone — a plain UTC wall-time string
 * when the row has no org (the same help as unauthenticated tenant calls).
 */
public final class ConsoleTimes {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private ConsoleTimes() {
    }

    /**
     * Format a UTC wall-time value in the subject org IANA zone
     * ({@code organizations.timezone}), falling back to UTC on an unknown zone.
     */
    public static String inZone(LocalDateTime utc, String timezone) {
        if (utc == null) {
            return null;
        }
        ZoneId zone = resolve(timezone);
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).format(ISO);
    }

    static ZoneId resolve(String timezone) {
        if (timezone == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
