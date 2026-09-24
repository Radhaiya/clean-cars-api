package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.BikeFuelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
/**
 * Create/update payload for a bike. Only {@code customerId} and {@code bikeNumber}
 * are required; everything else is optional. {@code orgId} comes from the token.
 */
public record BikeRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 255) String bikeNumber,
        UUID brandId,
        UUID modelId,
        Integer year,
        @Size(max = 255) String color,
        BikeFuelType fuelType,
        @Size(max = 255) String chassisNumber,
        @Size(max = 255) String engineNumber,
        String comments
) {
    public void applyTo(Bike bike) {
        bike.setCustomerId(customerId);
        bike.setBikeNumber(bikeNumber.trim());
        bike.setBrandId(brandId);
        bike.setModelId(modelId);
        bike.setYear(year);
        bike.setColor(color);
        bike.setFuelType(fuelType);
        bike.setChassisNumber(chassisNumber);
        bike.setEngineNumber(engineNumber);
        bike.setComments(comments);
    }
}
