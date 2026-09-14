package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.time.LocalDateTime;
import java.util.List;

/** Single-customer view: the customer plus the cars they own (car number, brand, model). */
public record CustomerAndCarsResponse(
        Long id,
        String name,
        String phone,
        String altPhone,
        String email,
        String address,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CustomerCarSummary> cars
) {
    public static CustomerAndCarsResponse of(Customer c, List<CustomerCarSummary> cars) {
        return new CustomerAndCarsResponse(
                c.getId(),
                c.getName(),
                c.getPhone(),
                c.getAltPhone(),
                c.getEmail(),
                c.getAddress(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                cars);
    }
}
