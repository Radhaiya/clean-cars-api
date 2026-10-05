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

    @Test
    void phoneLengthFollowsTheCountry() {
        assertEquals(null, CountryDialCodes.phoneLengthError("IN", "+91", "98765 43210"));
        assertEquals(null, CountryDialCodes.phoneLengthError("IN", "+91", "+91 9876543210"));
        assertTrue(CountryDialCodes.phoneLengthError("IN", "+91", "987654321").contains("10 digits"));
        assertTrue(CountryDialCodes.phoneLengthError("IN", "+91", "98765432101") != null);
        assertEquals(null, CountryDialCodes.phoneLengthError("AE", "+971", "501234567"));
        assertEquals(null, CountryDialCodes.phoneLengthError("BS", "+1242", "5550100"));
        assertTrue(CountryDialCodes.phoneLengthError("IN", "+91", "98765abcde") != null);
    }
}
