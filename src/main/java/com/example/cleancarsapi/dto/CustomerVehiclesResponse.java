package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Single-customer view: the customer plus the vehicles (cars + bikes) they own. */
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
        List<CustomerBikeSummary> bikes
) {
    public static CustomerVehiclesResponse of(Customer c, List<CustomerCarSummary> cars,
                                              List<CustomerBikeSummary> bikes) {
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
                bikes);
    }
}
