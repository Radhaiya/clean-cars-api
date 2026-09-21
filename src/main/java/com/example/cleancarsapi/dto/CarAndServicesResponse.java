package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.FuelType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Single-car view: the car plus its past service orders, newest first. */
public record CarAndServicesResponse(
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
        LocalDateTime updatedAt,
        //TODO Add Pagenation or a something like that
        List<CarServiceSummary> services
) {
    public static CarAndServicesResponse of(Car c, List<CarServiceSummary> services) {
        return new CarAndServicesResponse(
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
                c.getUpdatedAt(),
                services);
    }
}
