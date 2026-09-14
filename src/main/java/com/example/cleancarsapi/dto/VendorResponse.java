package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Vendor;

import java.time.LocalDateTime;

public record VendorResponse(Long id, String name, String contactPhone, String address, LocalDateTime createdAt) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(vendor.getId(), vendor.getName(), vendor.getContactPhone(),
                vendor.getAddress(), vendor.getCreatedAt());
    }
}
