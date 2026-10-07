package com.example.cleancarsapi.service;

import java.util.Locale;

/**
 * Comparison keys that ignore formatting: {@code MH 13 AB 1234}, {@code mh-13-ab-1234} and
 * {@code MH13AB1234} are the same vehicle; {@code 98765 43210} and {@code 9876543210} the same phone.
 * Stored values keep their display form; only duplicate checks and search use these keys.
 */
public final class NormalizedKeys {

    private NormalizedKeys() {
    }

    /** Uppercase letters and digits only — the key for car / bike numbers. */
    public static String vehicle(String number) {
        return number == null ? "" : number.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** Digits only — the key for phone numbers. */
    public static String phone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    /** Display form of a vehicle number: trimmed, upper-cased, inner whitespace collapsed to one space. */
    public static String vehicleDisplay(String number) {
        return number.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
