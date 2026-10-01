package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for recording ({@code POST}) or editing ({@code PUT}) one payment on a service order.
 * {@code amount} is required on a SPLIT order (positive, at most what is still remaining);
 * on a ONE_TIME order it is ignored — the payment is always the full order total.
 * {@code paymentDate} defaults to today in the org's timezone.
 */
public record PaymentRequest(
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull PaymentType paymentType,
        LocalDate paymentDate
) {
}
