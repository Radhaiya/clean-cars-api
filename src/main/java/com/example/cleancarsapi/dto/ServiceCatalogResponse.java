package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCatalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * Read projection for a service-catalog entry.
 *
 * <p>{@code price}, {@code gstPercentage} and {@code gstIncluded} are the stored inputs.
 * {@code netAmount} / {@code gstAmount} / {@code grossAmount} are derived here on every
 * read — the "final price" is never persisted. {@code categoryName} is resolved by the
 * caller (null when uncategorized).
 *
 * <ul>
 *   <li>{@code gstIncluded == true}  → {@code price} is the gross; net = price / (1 + rate).</li>
 *   <li>{@code gstIncluded == false} → {@code price} is the net; gross = price * (1 + rate).</li>
 *   <li>{@code gstPercentage} null/zero → net == gross == price, no GST.</li>
 * </ul>
 */
public record ServiceCatalogResponse(
        UUID id,
        String name,
        UUID categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal gstPercentage,
        boolean gstIncluded,
        BigDecimal netAmount,
        BigDecimal gstAmount,
        BigDecimal grossAmount,
        LocalDateTime createdAt
) {
    public static ServiceCatalogResponse from(ServiceCatalog entry, String categoryName) {
        BigDecimal base = entry.getPrice() == null ? BigDecimal.ZERO : entry.getPrice();
        GstBreakdown b = GstBreakdown.of(base, entry.getGstPercentage(), entry.isGstIncluded());

        return new ServiceCatalogResponse(
                entry.getId(),
                entry.getName(),
                entry.getCategoryId(),
                categoryName,
                base.setScale(2, RoundingMode.HALF_UP),
                entry.getGstPercentage(),
                entry.isGstIncluded(),
                b.net(),
                b.gst(),
                b.gross(),
                entry.getCreatedAt());
    }
}
