package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.exception.BadRequestException;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;

/**
 * Body for {@code PUT /api/organization}. All fields are optional and null-safe
 * trimmed — PUT is a full replace: a field left out of the body (or sent null)
 * clears the stored value. {@code name} and {@code timezone} are NOT NULL
 * columns, so a null there leaves the stored value untouched.
 */
public record OrganizationUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String tagline,
        @Size(max = 255) String contactPhone,
        @Size(max = 255) String contactEmail,
        @Size(max = 64) String timezone,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 255) String state,
        @Size(max = 255) String country,
        @Size(max = 255) String zipCode
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
