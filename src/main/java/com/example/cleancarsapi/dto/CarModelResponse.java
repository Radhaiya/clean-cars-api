package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarModel;

import java.time.LocalDateTime;

public record CarModelResponse(Long id, Long brandId, String name, LocalDateTime createdAt) {

    public static CarModelResponse from(CarModel model) {
        return new CarModelResponse(model.getId(), model.getBrandId(), model.getName(), model.getCreatedAt());
    }
}
