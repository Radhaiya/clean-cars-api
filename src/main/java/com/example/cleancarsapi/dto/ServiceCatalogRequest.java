package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCatalog;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;
/**
 * Create/update payload for a service-catalog entry. {@code orgId} comes from the token.
 *
 * <p>Required: {@code name}, {@code price} (the base price), and the GST inputs
 * ({@code gstPercentage} + {@code gstIncluded}). The final/net/gross amounts are
 * never sent or stored — they are computed from these fields on read.
 */
public record ServiceCatalogRequest(
        @NotBlank @Size(max = 255) String name,
        UUID categoryId,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal price,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal gstPercentage,
        boolean gstIncluded
) {
    public void applyTo(ServiceCatalog entry) {
        entry.setName(name.trim());
        entry.setCategoryId(categoryId);
        entry.setPrice(price);
        entry.setGstPercentage(gstPercentage);
        entry.setGstIncluded(gstIncluded);
    }
}
