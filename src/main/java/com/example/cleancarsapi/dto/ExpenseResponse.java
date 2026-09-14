package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Read projection for an expense. {@code unit*} is the per-unit breakdown;
 * {@code line*} is {@code unit* x quantity}. All derived on every read — the
 * "final amount" is never persisted. {@code categoryName} is resolved by the
 * caller (null when uncategorized).
 */
public record ExpenseResponse(
        Long id,
        String title,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        BigDecimal gstPercentage,
        boolean gstIncluded,
        int quantity,
        BigDecimal unitNet,
        BigDecimal unitGst,
        BigDecimal unitGross,
        BigDecimal lineNet,
        BigDecimal lineGst,
        BigDecimal lineGross,
        String notes,
        LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense expense, String categoryName) {
        BigDecimal base = expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount();
        GstBreakdown unit = GstBreakdown.of(base, expense.getGstPercentage(), expense.isGstIncluded());
        GstBreakdown line = unit.times(expense.getQuantity());

        return new ExpenseResponse(
                expense.getId(),
                expense.getTitle(),
                expense.getCategoryId(),
                categoryName,
                base.setScale(2, RoundingMode.HALF_UP),
                expense.getGstPercentage(),
                expense.isGstIncluded(),
                expense.getQuantity(),
                unit.net(), unit.gst(), unit.gross(),
                line.net(), line.gst(), line.gross(),
                expense.getNotes(),
                expense.getCreatedAt());
    }
}
