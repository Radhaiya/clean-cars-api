package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for an expense category. {@code orgId} comes from the token. */
public record ExpenseCategoryRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name
) {
    public void applyTo(ExpenseCategory category) {
        category.setName(name.trim());
    }
}
