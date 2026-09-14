package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ExpenseCategory;

import java.time.LocalDateTime;

public record ExpenseCategoryResponse(Long id, String name, LocalDateTime createdAt) {

    public static ExpenseCategoryResponse from(ExpenseCategory category) {
        return new ExpenseCategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
