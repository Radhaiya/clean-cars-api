package com.example.cleancarsapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create/update payload for an AMC variant: tenure and frequency in months (tenure must be a
 * multiple of the interval — checked in the service) and one price row per plan service.
 */
public record AmcVariantRequest(
        @NotNull @Min(1) @Max(600) Integer tenureMonths,
        @NotNull @Min(1) @Max(600) Integer intervalMonths,
        @NotEmpty @Valid List<Row> rows
) {
    /** The quantity (null = 1), price and tax of one plan service; {@code serviceName} must match a plan service. */
    public record Row(
            @NotBlank @Size(max = 255) String serviceName,
            @Positive @Max(1000) Integer quantity,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal price,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal taxPercentage,
            boolean taxIncluded
    ) {
    }
}
