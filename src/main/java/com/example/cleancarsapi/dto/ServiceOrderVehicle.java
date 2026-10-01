package com.example.cleancarsapi.dto;

import java.util.UUID;

/**
 * The vehicle a service order was opened for — a car or a bike ({@code type} = CAR / BIKE).
 * Resolved even when the vehicle is soft-deleted ({@code isDeleted}); brand/model are names,
 * {@code fuelType} is the enum constant name for the UI to label.
 */
public record ServiceOrderVehicle(
        UUID id,
        String type,
        String number,
        String brandName,
        String modelName,
        Integer year,
        String color,
        String fuelType,
        boolean isDeleted
) {
}
