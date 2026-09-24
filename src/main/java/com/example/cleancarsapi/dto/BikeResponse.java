package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.BikeFuelType;

import java.time.LocalDateTime;
import java.util.UUID;

public record BikeResponse(
        UUID id,
        UUID customerId,
        String bikeNumber,
        UUID brandId,
        UUID modelId,
        Integer year,
        String color,
        BikeFuelType fuelType,
        String chassisNumber,
        String engineNumber,
        String comments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BikeResponse from(Bike b) {
        return new BikeResponse(
                b.getId(),
                b.getCustomerId(),
                b.getBikeNumber(),
                b.getBrandId(),
                b.getModelId(),
                b.getYear(),
                b.getColor(),
                b.getFuelType(),
                b.getChassisNumber(),
                b.getEngineNumber(),
                b.getComments(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
