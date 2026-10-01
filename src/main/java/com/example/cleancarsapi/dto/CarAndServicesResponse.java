package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.FuelType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Single-car view: the car plus its past service orders, newest first. */
public record CarAndServicesResponse(
        UUID id,
        UUID customerId,
        String customerName,
        boolean isCustomerDeleted,
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
        List<CarServiceSummary> services,
        long totalServices,
        BigDecimal totalRevenue,
        Integer lastOdometerReading
) {
    public static CarAndServicesResponse of(Car c, Customer customer, VehicleHistory history) {
        return new CarAndServicesResponse(
                c.getId(),
                c.getCustomerId(),
                customer == null ? null : customer.getName(),
                customer != null && customer.isDeleted(),
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
                history.services(),
                history.totalServices(),
                history.totalRevenue(),
                history.lastOdometerReading());
    }
}
