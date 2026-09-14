package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/subscription/trial}. The caller must be an org-less
 * user; this both creates their organization (1 user : 1 org) and opens the trial.
 * There is exactly one dedicated Trial plan, resolved automatically — the caller
 * never picks a plan here.
 */
public record StartTrialRequest(
        @NotBlank @Size(max = 255) String orgName,
        @Size(max = 255) String contactPhone,
        @Size(max = 255) String contactEmail,
        @Size(max = 255) String address
) {
}
