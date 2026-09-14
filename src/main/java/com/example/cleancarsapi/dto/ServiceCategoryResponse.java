package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCategory;

import java.time.LocalDateTime;

public record ServiceCategoryResponse(Long id, String name, LocalDateTime createdAt) {

    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
