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
        @NotBlank @Size(max = 255) String name,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 255) String> serviceNames
) {
}
