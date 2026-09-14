package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.FuelType;

import java.time.LocalDateTime;

public record CarResponse(
        Long id,
        Long customerId,
        String carNumber,
        Long brandId,
        Long modelId,
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
