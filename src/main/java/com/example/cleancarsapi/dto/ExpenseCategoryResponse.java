package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ExpenseCategory;

import java.time.LocalDateTime;
import java.util.UUID;
public record ExpenseCategoryResponse(UUID id, String name, LocalDateTime createdAt) {

    public static ExpenseCategoryResponse from(ExpenseCategory category) {
        return new ExpenseCategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
