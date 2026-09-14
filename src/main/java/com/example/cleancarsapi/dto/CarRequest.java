package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.FuelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a car. Only {@code customerId} and {@code carNumber}
 * are required; everything else is optional. {@code orgId} comes from the token.
 */
public record CarRequest(
        @NotNull Long customerId,
        @NotBlank @Size(max = 255) String carNumber,
        Long brandId,
        Long modelId,
        Integer year,
        @Size(max = 255) String color,
        FuelType fuelType,
        @Size(max = 255) String chassisVin,
        String comments
) {
    public void applyTo(Car car) {
        car.setCustomerId(customerId);
        car.setCarNumber(carNumber.trim());
        car.setBrandId(brandId);
        car.setModelId(modelId);
        car.setYear(year);
        car.setColor(color);
        car.setFuelType(fuelType);
        car.setChassisVin(chassisVin);
        car.setComments(comments);
    }
}
