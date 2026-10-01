package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payment inside a create/update order request. {@code id} matches an existing payment
 * to keep it (and who recorded it); no id creates a new one. {@code amount} follows the same
 * rule as {@link PaymentRequest}: required on SPLIT, ignored on ONE_TIME.
 */
public record ServiceOrderPaymentLine(
        UUID id,
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull PaymentType paymentType,
        LocalDate paymentDate
) {
}
