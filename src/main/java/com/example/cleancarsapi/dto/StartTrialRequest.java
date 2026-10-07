package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/subscription/trial}. The caller must be an org-less
 * user; this both creates their organization (1 user : 1 org) and opens the trial.
 * There is exactly one dedicated Trial plan, resolved automatically — the caller
 * never picks a plan here.
 */
public record StartTrialRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.ORG_NAME_MAX, message = FieldLimits.ORG_NAME_MSG) String orgName,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 3) String currency,
        @Size(max = FieldLimits.PHONE_MAX, message = FieldLimits.PHONE_MSG) @Pattern(regexp = FieldLimits.PHONE_RE, message = FieldLimits.PHONE_MSG) String contactPhone,
        @Email @Size(max = FieldLimits.EMAIL_MAX) String contactEmail,
        /** Optional tax label (e.g. {@code VAT}) — null = unset; rate lives per line item. */
        @Size(max = 64) String taxName,
        /** ISO 3166-1 alpha-2 phone country (e.g. {@code IN}) — compulsory at onboarding. */
        @NotBlank @Size(max = 2) String countryCode
) {
}
