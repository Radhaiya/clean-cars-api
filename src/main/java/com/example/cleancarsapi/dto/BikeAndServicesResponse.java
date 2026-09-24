package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.BikeFuelType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Single-bike view: the bike plus its past service orders, newest first. */
public record BikeAndServicesResponse(
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
        LocalDateTime updatedAt,
        List<CarServiceSummary> services
) {
    public static BikeAndServicesResponse of(Bike b, List<CarServiceSummary> services) {
        return new BikeAndServicesResponse(
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
                b.getUpdatedAt(),
                services);
    }
}
