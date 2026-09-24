package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeBrand;

import java.time.LocalDateTime;
import java.util.UUID;

public record BikeBrandResponse(
        UUID id,
        String name,
        LocalDateTime createdAt
) {
    public static BikeBrandResponse from(BikeBrand b) {
        return new BikeBrandResponse(b.getId(), b.getName(), b.getCreatedAt());
    }
}
