package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeModel;

import java.time.LocalDateTime;
import java.util.UUID;

public record BikeModelResponse(
        UUID id,
        UUID brandId,
        String name,
        LocalDateTime createdAt
) {
    public static BikeModelResponse from(BikeModel m) {
        return new BikeModelResponse(m.getId(), m.getBrandId(), m.getName(), m.getCreatedAt());
    }
}
