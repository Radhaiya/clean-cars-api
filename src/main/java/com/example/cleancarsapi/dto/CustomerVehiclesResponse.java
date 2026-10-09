package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * Single-customer view: the customer plus the vehicles (cars + bikes) they own, and the roll-ups across all of
 * them — services and revenue as on each vehicle's detail (non-cancelled orders, gross), AMC revenue (gross sales;
 * zero when the plan has no AMC) kept separate.
 */
public record CustomerVehiclesResponse(
        UUID id,
        String name,
        String phone,
        String altPhone,
        String email,
        String address,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CustomerCarSummary> cars,
        List<CustomerBikeSummary> bikes,
        long totalServices,
        BigDecimal totalRevenue,
        BigDecimal amcRevenue
) {
    public static CustomerVehiclesResponse of(Customer c, List<CustomerCarSummary> cars,
                                              List<CustomerBikeSummary> bikes, long totalServices,
                                              BigDecimal totalRevenue, BigDecimal amcRevenue) {
        return new CustomerVehiclesResponse(
                c.getId(),
                c.getName(),
                c.getPhone(),
                c.getAltPhone(),
                c.getEmail(),
                c.getAddress(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                cars,
                bikes,
                totalServices,
                totalRevenue,
                amcRevenue);
    }
}
