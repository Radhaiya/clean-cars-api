package com.example.cleancarsapi.dto.internal;

import com.example.cleancarsapi.entity.FeatureProperty;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code feature_properties} row, as listed by {@code GET /internal/api/properties}. */
public record FeaturePropertyResponse(UUID id, String key, JsonNode value, String description,
                                      String updatedByEmail, LocalDateTime updatedAt) {

    public static FeaturePropertyResponse from(FeatureProperty row, JsonNode value) {
        return new FeaturePropertyResponse(row.getId(), row.getKey(), value, row.getDescription(),
                row.getUpdatedByEmail(), row.getUpdatedAt());
    }
}
