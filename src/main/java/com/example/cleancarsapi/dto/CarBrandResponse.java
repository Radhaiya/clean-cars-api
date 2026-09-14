package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarBrand;

import java.time.LocalDateTime;

public record CarBrandResponse(Long id, String name, LocalDateTime createdAt) {

    public static CarBrandResponse from(CarBrand brand) {
        return new CarBrandResponse(brand.getId(), brand.getName(), brand.getCreatedAt());
    }
}
