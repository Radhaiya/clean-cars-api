package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.BikeFuelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
/**
 * Create/update payload for a bike. Only {@code customerId} and {@code bikeNumber}
 * are required; everything else is optional. {@code orgId} comes from the token.
 */
public record BikeRequest(
        @NotNull UUID customerId,
        @NotBlank @Pattern(regexp = FieldLimits.VEHICLE_NUMBER_RE, message = FieldLimits.VEHICLE_NUMBER_MSG) String bikeNumber,
        UUID brandId,
        UUID modelId,
        Integer year,
        @Size(max = 30, message = "Colour must be at most 30 characters") String color,
        BikeFuelType fuelType,
        @Size(max = 17, message = "Chassis number must be at most 17 characters") @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Chassis number must be letters or digits only") String chassisNumber,
        @Size(max = 30, message = "Engine number must be at most 30 characters") String engineNumber,
        @Size(max = FieldLimits.NOTES_MAX) String comments
) {
    public void applyTo(Bike bike) {
        bike.setCustomerId(customerId);
        bike.setBikeNumber(com.example.cleancarsapi.service.NormalizedKeys.vehicleDisplay(bikeNumber));
        bike.setBrandId(brandId);
        bike.setModelId(modelId);
        bike.setYear(year);
        bike.setColor(trimToNull(color));
        bike.setFuelType(fuelType);
        bike.setChassisNumber(chassisNumber == null || chassisNumber.isBlank() ? null : chassisNumber.trim().toUpperCase(java.util.Locale.ROOT));
        bike.setEngineNumber(engineNumber);
        bike.setComments(comments);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
