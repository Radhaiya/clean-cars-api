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
 * Create/update payload for an expense. {@code orgId} comes from the token.
 *
 * <p>Required: {@code title}, {@code amount} (the unit amount), {@code quantity}, and
 * the GST inputs ({@code gstPercentage} + {@code gstIncluded}). The net/GST/gross
 * amounts are never sent or stored — they are computed from these fields on read.
 */
public record ExpenseRequest(
        @NotBlank @Size(max = 255) String title,
        Long categoryId,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal gstPercentage,
        boolean gstIncluded,
        @NotNull @Positive Integer quantity,
        String notes
) {
    public void applyTo(Expense expense) {
        expense.setTitle(title.trim());
        expense.setCategoryId(categoryId);
        expense.setAmount(amount);
        expense.setGstPercentage(gstPercentage);
        expense.setGstIncluded(gstIncluded);
        expense.setQuantity(quantity);
        expense.setNotes(notes);
    }
}
