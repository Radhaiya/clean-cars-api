package com.example.cleancarsapi;

import com.example.cleancarsapi.service.CountryDialCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryDialCodesTest {

    @Test
    void optionCarriesNameDialCodeAndFlag() {
        var india = CountryDialCodes.find("in").orElseThrow();
        assertEquals("India", india.name());
        assertEquals("+91", india.dialCode());
        assertEquals("🇮🇳", india.flag());
        assertEquals("🇮🇳 India (+91)", india.label());
    }

    @Test
    void everyListedCountryHasADistinctIsoAndDialCode() {
        var options = CountryDialCodes.options();
        assertTrue(options.size() > 200);
        assertEquals(options.size(), options.stream().map(o -> o.isoCode()).distinct().count());
    }

    @Test
    void phoneSuggestionUsesLongestPrefixAndPrimaryCountry() {
        assertEquals("IN", CountryDialCodes.isoForPhone("+919876543210").orElseThrow());
        assertEquals("US", CountryDialCodes.isoForPhone("+14155550100").orElseThrow());
        assertEquals("BS", CountryDialCodes.isoForPhone("+12425550100").orElseThrow());
        assertTrue(CountryDialCodes.isoForPhone("9876543210").isEmpty());
    }

    @Test
    void currencySuggestionOnlyWhenUnambiguous() {
        assertEquals("IN", CountryDialCodes.isoForCurrency("INR").orElseThrow());
        assertTrue(CountryDialCodes.isoForCurrency("EUR").isEmpty());
    }
}
