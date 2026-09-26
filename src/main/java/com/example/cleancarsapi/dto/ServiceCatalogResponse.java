package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCatalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * Read projection for a service-catalog entry.
 *
 * <p>{@code price}, {@code taxPercentage} and {@code taxIncluded} are the stored inputs.
 * {@code netAmount} / {@code taxAmount} / {@code grossAmount} are derived here on every
 * read — the "final price" is never persisted. {@code categoryName} is resolved by the
 * caller (null when uncategorized).
 *
 * <ul>
 *   <li>{@code taxIncluded == true}  → {@code price} is the gross; net = price / (1 + rate).</li>
 *   <li>{@code taxIncluded == false} → {@code price} is the net; gross = price * (1 + rate).</li>
 *   <li>{@code taxPercentage} null/zero → net == gross == price, no tax.</li>
 * </ul>
 */
public record ServiceCatalogResponse(
        UUID id,
        String name,
        UUID categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal taxPercentage,
        boolean taxIncluded,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        LocalDateTime createdAt
) {
    public static ServiceCatalogResponse from(ServiceCatalog entry, String categoryName) {
        BigDecimal base = entry.getPrice() == null ? BigDecimal.ZERO : entry.getPrice();
        TaxBreakdown b = TaxBreakdown.of(base, entry.getTaxPercentage(), entry.isTaxIncluded());

        return new ServiceCatalogResponse(
                entry.getId(),
                entry.getName(),
                entry.getCategoryId(),
                categoryName,
                base.setScale(2, RoundingMode.HALF_UP),
                entry.getTaxPercentage(),
                entry.isTaxIncluded(),
                b.net(),
                b.tax(),
                b.gross(),
                entry.getCreatedAt());
    }
}
