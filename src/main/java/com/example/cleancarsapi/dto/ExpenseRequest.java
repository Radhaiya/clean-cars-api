package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Expense;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create/update payload for one expense row. {@code categoryName} is validated
 * against the org's {@code expense_categories} by the create/update service
 * (the dropdown supplies it, this only stops typos); {@code orgId} comes from
 * the token.
 */
public record ExpenseRequest(
        @NotBlank @Size(max = 255) String categoryName,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal gstPercentage,
        boolean gstIncluded,
        @NotNull @Positive Integer quantity,
        String notes
) {
    public void applyTo(Expense expense) {
        expense.setCategoryName(categoryName.trim());
        expense.setAmount(amount);
        expense.setGstPercentage(gstPercentage);
        expense.setGstIncluded(gstIncluded);
        expense.setQuantity(quantity);
        expense.setNotes(notes);
    }
}
