package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.FuelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
/**
 * Create/update payload for a car. Only {@code customerId} and {@code carNumber}
 * are required; everything else is optional. {@code orgId} comes from the token.
 */
public record CarRequest(
        @NotNull UUID customerId,
        @NotBlank @Pattern(regexp = FieldLimits.VEHICLE_NUMBER_RE, message = FieldLimits.VEHICLE_NUMBER_MSG) String carNumber,
        UUID brandId,
        UUID modelId,
        Integer year,
        @Size(max = 30, message = "Colour must be at most 30 characters") String color,
        FuelType fuelType,
        @Size(max = 17, message = "Chassis / VIN must be at most 17 characters") @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Chassis / VIN must be letters or digits only") String chassisVin,
        @Size(max = FieldLimits.NOTES_MAX) String comments
) {
    public void applyTo(Car car) {
        car.setCustomerId(customerId);
        car.setCarNumber(com.example.cleancarsapi.service.NormalizedKeys.vehicleDisplay(carNumber));
        car.setBrandId(brandId);
        car.setModelId(modelId);
        car.setYear(year);
        car.setColor(trimToNull(color));
        car.setFuelType(fuelType);
        car.setChassisVin(chassisVin == null || chassisVin.isBlank() ? null : chassisVin.trim().toUpperCase(java.util.Locale.ROOT));
        car.setComments(comments);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
