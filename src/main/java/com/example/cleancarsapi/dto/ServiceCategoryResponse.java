package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCategory;

import java.time.LocalDateTime;
import java.util.UUID;
public record ServiceCategoryResponse(UUID id, String name, LocalDateTime createdAt) {

    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
