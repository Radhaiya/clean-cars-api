package com.example.cleancarsapi.dto.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Body of {@code PUT /internal/api/properties/{key}}. {@code description} is left as-is when omitted. */
public record FeaturePropertyUpdateRequest(@NotNull JsonNode value, @Size(max = 255) String description) {
}
