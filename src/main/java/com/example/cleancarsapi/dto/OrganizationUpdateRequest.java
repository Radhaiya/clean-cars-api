package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.service.ReferenceDataService;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;
import java.util.Currency;

/**
 * Body for {@code PUT /api/organization}. All fields are optional and null-safe
 * trimmed — PUT is a full replace: a field left out of the body (or sent null)
 * clears the stored value. {@code name}, {@code timezone} and {@code currency} are NOT NULL
 * columns, so a null there leaves the stored value untouched.
 *
 * {@code countryCode} (the org's phone country) is the exception to full-replace: null
 * leaves the stored value untouched, so a client that predates it can't wipe it.
 *
 * {@code taxName} is a free label (≤64) — the {@code GET /api/reference} tax list is a
 * picker suggestion only; a custom name is stored verbatim. Null clears the label.
 */
public record OrganizationUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String tagline,
        @Size(max = 255) String contactPhone,
        @Size(max = 255) String contactEmail,
        @Size(max = 64) String timezone,
        @Size(max = 3) String currency,
        @Size(max = 64) String taxName,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 255) String state,
        @Size(max = 255) String country,
        @Size(max = 255) String zipCode,
        /** ISO 3166-1 alpha-2 phone country (see {@code GET /api/reference} countryCodes) — null = unchanged. */
        @Size(max = 2) String countryCode
) {

    /** Copy onto the managed entity, shared by the PUT endpoint. */
    public void applyTo(Organization org) {
        if (name != null) {
            org.setName(name.trim());
        }
        org.setTagline(trimToNull(tagline));
        org.setContactPhone(trimToNull(contactPhone));
        org.setContactEmail(trimToNull(contactEmail));
        if (timezone != null) {
            org.setTimezone(parseTimezone(timezone));
        }
        if (currency != null) {
            Currency resolved = ReferenceDataService.requireCurrency(currency);
            org.setCurrencyCode(resolved.getCurrencyCode());
            org.setCurrencySymbol(ReferenceDataService.symbolOf(resolved));
        }
        if (countryCode != null && !countryCode.isBlank()) {
            var resolved = ReferenceDataService.requireCountryCode(countryCode);
            org.setPhoneCountryIso(resolved.isoCode());
            org.setPhoneDialCode(resolved.dialCode());
        }
        org.setTaxName(trimToNull(taxName));
        org.setAddressLine1(trimToNull(addressLine1));
        org.setAddressLine2(trimToNull(addressLine2));
        org.setState(trimToNull(state));
        org.setCountry(trimToNull(country));
        org.setZipCode(trimToNull(zipCode));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Bean validation can't check IANA validity — reject anything {@link ZoneId} can't resolve. */
    private static String parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception e) {
            throw new BadRequestException("Unknown timezone: " + timezone.trim());
        }
    }
}
