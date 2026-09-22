package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ReferenceDataResponse;
import com.example.cleancarsapi.dto.ReferenceDataResponse.CurrencyOption;
import com.example.cleancarsapi.dto.ReferenceDataResponse.TimezoneOption;
import com.example.cleancarsapi.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Timezone and currency pick-lists for {@code GET /api/reference}, plus the currency
 * lookup the org create/update paths share. Timezones are one hand-picked main zone per
 * standard UTC offset; currencies are the ones some country currently uses (derived from
 * the JDK's locales, so obsolete ISO codes drop out).
 */
@Service
public class ReferenceDataService {

    /** One main zone per standard offset, west to east — the picker shows each offset once. */
    private static final List<ZoneId> MAIN_ZONES = Stream.of(
            "Pacific/Pago_Pago",    // -11:00
            "Pacific/Honolulu",     // -10:00
            "Pacific/Marquesas",    // -9:30
            "America/Anchorage",    // -9:00
            "America/Los_Angeles",  // -8:00
            "America/Denver",       // -7:00
            "America/Chicago",      // -6:00
            "America/New_York",     // -5:00
            "America/Halifax",      // -4:00
            "America/St_Johns",     // -3:30
            "America/Sao_Paulo",    // -3:00
            "America/Noronha",      // -2:00
            "Atlantic/Azores",      // -1:00
            "Europe/London",        // +0:00
            "Europe/Paris",         // +1:00
            "Africa/Cairo",         // +2:00
            "Europe/Moscow",        // +3:00
            "Asia/Tehran",          // +3:30
            "Asia/Dubai",           // +4:00
            "Asia/Kabul",           // +4:30
            "Asia/Karachi",         // +5:00
            "Asia/Kolkata",         // +5:30
            "Asia/Kathmandu",       // +5:45
            "Asia/Dhaka",           // +6:00
            "Asia/Yangon",          // +6:30
            "Asia/Bangkok",         // +7:00
            "Asia/Singapore",       // +8:00
            "Australia/Eucla",      // +8:45
            "Asia/Tokyo",           // +9:00
            "Australia/Adelaide",   // +9:30
            "Australia/Sydney",     // +10:00
            "Australia/Lord_Howe",  // +10:30
            "Pacific/Noumea",       // +11:00
            "Pacific/Auckland",     // +12:00
            "Pacific/Chatham",      // +12:45
            "Pacific/Tongatapu",    // +13:00
            "Pacific/Kiritimati"    // +14:00
    ).map(ZoneId::of).toList();

    /** Currencies in active use — static data, computed once. */
    private static final List<Currency> CURRENCIES = Locale.availableLocales()
            .filter(locale -> !locale.getCountry().isEmpty())
            .map(ReferenceDataService::currencyOf)
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(Currency::getCurrencyCode))
            .toList();

    public ReferenceDataResponse list() {
        return new ReferenceDataResponse(timezones(), currencies());
    }

    /** Resolve a client-sent code (case-insensitive) to one of the listed currencies, else 400. */
    public static Currency requireCurrency(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return CURRENCIES.stream()
                .filter(c -> c.getCurrencyCode().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown currency: " + code.trim()));
    }

    /** English-locale symbol — {@code $} for USD, {@code ₹} for INR; falls back to the code itself. */
    public static String symbolOf(Currency currency) {
        return currency.getSymbol(Locale.ENGLISH);
    }

    // Labels use the standard (non-DST) offset, so they don't shift with the season.
    private static List<TimezoneOption> timezones() {
        Instant now = Instant.now();
        return MAIN_ZONES.stream()
                .map(z -> new TimezoneOption(z.getId(),
                        z.getId() + " (" + gmtLabel(z.getRules().getStandardOffset(now)) + ")"))
                .toList();
    }

    private static List<CurrencyOption> currencies() {
        return CURRENCIES.stream()
                .map(c -> {
                    String code = c.getCurrencyCode();
                    String symbol = symbolOf(c);
                    return new CurrencyOption(code, symbol,
                            symbol.equals(code) ? code : code + " (" + symbol + ")");
                })
                .toList();
    }

    /** {@code GMT+5:30}, {@code GMT-3:00}, {@code GMT+0:00}. */
    private static String gmtLabel(ZoneOffset offset) {
        int total = offset.getTotalSeconds();
        int abs = Math.abs(total);
        return String.format("GMT%s%d:%02d", total < 0 ? "-" : "+", abs / 3600, (abs % 3600) / 60);
    }

    private static Currency currencyOf(Locale locale) {
        try {
            return Currency.getInstance(locale);
        } catch (IllegalArgumentException e) {
            return null; // locale's country has no ISO 3166 code / currency
        }
    }
}
