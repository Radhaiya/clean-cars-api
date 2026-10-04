package com.example.cleancarsapi.dto;

import java.util.List;

/**
 * Body of {@code GET /api/reference} — the pick-lists for the org form
 * (timezones, currencies, taxes, phone country codes). The client
 * shows {@code label} and sends back {@code id} (timezone) / {@code code} (currency) /
 * {@code label} (tax — the label itself is the stored value; a custom tax name is
 * sent as any trimmed string up to 64 chars).
 */
public record ReferenceDataResponse(
        List<TimezoneOption> timezones,
        List<CurrencyOption> currencies,
        List<TaxOption> taxes,
        List<CountryCodeOption> countryCodes
) {

    /**
     * {@code {isoCode: "IN", name: "India", dialCode: "+91", flag: "🇮🇳", label: "🇮🇳 India (+91)"}} —
     * the client shows {@code label} and sends back {@code isoCode} (the dial code is resolved
     * server-side; several countries share +1).
     */
    public record CountryCodeOption(String isoCode, String name, String dialCode, String flag, String label) {
    }

    /** {@code {id: "Asia/Kolkata", label: "Asia/Kolkata (GMT+5:30)"}} — offset as of now. */
    public record TimezoneOption(String id, String label) {
    }

    /** {@code {code: "USD", symbol: "$", label: "USD ($)"}}. */
    public record CurrencyOption(String code, String symbol, String label) {
    }

    /** {@code {label: "VAT"}} — one entry per tax family; no id, the label is the value. */
    public record TaxOption(String label) {
    }
}
