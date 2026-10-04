package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/organization/country-code} — sets only the org's phone
 * country, leaving every other org field alone (unlike the full-replace
 * {@code PUT /api/organization}). {@code countryCode} is an ISO 3166-1 alpha-2 code
 * from {@code GET /api/reference} countryCodes.
 */
public record OrganizationCountryCodeRequest(
        @NotBlank @Size(max = 2) String countryCode
) {
}
