package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.FuelType;

import java.time.LocalDateTime;
import java.util.UUID;
public record CarResponse(
        UUID id,
        UUID customerId,
        String carNumber,
        UUID brandId,
        UUID modelId,
        Integer year,
        String color,
        FuelType fuelType,
        String chassisVin,
        String comments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CarResponse from(Car c) {
        return new CarResponse(
                c.getId(),
                c.getCustomerId(),
                c.getCarNumber(),
                c.getBrandId(),
                c.getModelId(),
                c.getYear(),
                c.getColor(),
                c.getFuelType(),
                c.getChassisVin(),
                c.getComments(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
