package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.InvoiceTemplate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code PUT /api/organization/invoice-settings} — the org's invoice template and accent
 * colour. Sets only these two fields, leaving every other org field alone.
 */
public record OrganizationInvoiceSettingsRequest(
        @NotNull InvoiceTemplate template,
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color must be a #RRGGBB hex value") String color
) {
}
