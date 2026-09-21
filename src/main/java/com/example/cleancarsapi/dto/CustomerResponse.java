package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Customer;

import java.time.LocalDateTime;
import java.util.UUID;
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String altPhone,
        String email,
        String address,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getPhone(),
                c.getAltPhone(),
                c.getEmail(),
                c.getAddress(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
