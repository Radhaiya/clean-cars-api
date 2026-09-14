package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One line on a service order.
 *
 * <p>{@code serviceCatalogId} is used only in the service layer to seed the fields
 * below from the current catalog pricing — it is never persisted. Any field the
 * caller supplies overrides the seeded value. With no {@code serviceCatalogId} the
 * line is fully custom and must carry its own {@code serviceName} + {@code basePrice}.
 */
public record ServiceOrderItemRequest(
        Long serviceCatalogId,
        @Size(max = 255) String serviceName,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal gstPercentage,
        Boolean gstIncluded,
        @Positive Integer quantity,
        String notes
) {
}
