package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create payload for an AMC plan: its name and its fixed, ordered service names. The service
 * names cannot be changed afterwards — a bundle with different services is a different plan.
 */
public record AmcPlanRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String> serviceNames
) {
}
