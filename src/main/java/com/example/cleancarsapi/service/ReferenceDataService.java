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
import java.util.regex.Pattern;

/**
 * Timezone and currency pick-lists for {@code GET /api/reference}, plus the currency
 * lookup the org create/update paths share. Timezones are the JDK's Region/City ids
 * (no {@code Etc/}, {@code SystemV/} or 3-letter aliases); currencies are the ones some
 * country currently uses (derived from the JDK's locales, so obsolete ISO codes drop out).
 */
@Service
public class ReferenceDataService {

    private static final Pattern REGION_CITY = Pattern.compile(
            "(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+");

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

    // Offsets are "now", so a DST zone's label follows the season; recomputed per call.
    private static List<TimezoneOption> timezones() {
        Instant now = Instant.now();
        return ZoneId.getAvailableZoneIds().stream()
                .filter(id -> REGION_CITY.matcher(id).matches())
                .map(ZoneId::of)
                .sorted(Comparator.comparing((ZoneId z) -> z.getRules().getOffset(now))
                        .reversed()
                        .thenComparing(ZoneId::getId))
                .map(z -> new TimezoneOption(z.getId(),
                        z.getId() + " (" + gmtLabel(z.getRules().getOffset(now)) + ")"))
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
