package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * Read projection for one expense row. {@code categoryName} is the denormalized
 * label stored on the row itself (may no longer be in the dropdown after the
 * category was deleted). Only the stored inputs are returned — the
 * net/GST/gross breakdown is the UI's job if it needs one.
 */
public record ExpenseResponse(
        UUID id,
        String categoryName,
        BigDecimal amount,
        BigDecimal gstPercentage,
        boolean gstIncluded,
        int quantity,
        String notes,
        LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getCategoryName(),
                expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount().setScale(2, RoundingMode.HALF_UP),
                expense.getGstPercentage(),
                expense.isGstIncluded(),
                expense.getQuantity(),
                expense.getNotes(),
                expense.getCreatedAt());
    }
}
