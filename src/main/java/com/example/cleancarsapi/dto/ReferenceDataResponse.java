package com.example.cleancarsapi.dto;

import java.util.List;

/**
 * Body of {@code GET /api/reference} — the pick-lists for the org form. The client
 * shows {@code label} and sends back {@code id} (timezone) / {@code code} (currency).
 */
public record ReferenceDataResponse(
        List<TimezoneOption> timezones,
        List<CurrencyOption> currencies
) {

    /** {@code {id: "Asia/Kolkata", label: "Asia/Kolkata (GMT+5:30)"}} — offset as of now. */
    public record TimezoneOption(String id, String label) {
    }

    /** {@code {code: "USD", symbol: "$", label: "USD ($)"}}. */
    public record CurrencyOption(String code, String symbol, String label) {
    }
}
