package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * Read projection for a service-order line. {@code unit*} is the per-unit
 * breakdown; {@code line*} is {@code unit* x quantity}. All derived on read.
 */
public record ServiceOrderItemResponse(
        UUID id,
        String serviceName,
        BigDecimal basePrice,
        BigDecimal taxPercentage,
        boolean taxIncluded,
        int quantity,
        BigDecimal unitNet,
        BigDecimal unitTax,
        BigDecimal unitGross,
        BigDecimal lineNet,
        BigDecimal lineTax,
        BigDecimal lineGross,
        String notes,
        LocalDateTime createdAt
) {
    public static ServiceOrderItemResponse from(ServiceOrderItem item) {
        TaxBreakdown unit = TaxBreakdown.of(item.getBasePrice(), item.getTaxPercentage(), item.isTaxIncluded());
        TaxBreakdown line = unit.times(item.getQuantity());
        return new ServiceOrderItemResponse(
                item.getId(),
                item.getServiceName(),
                item.getBasePrice().setScale(2, RoundingMode.HALF_UP),
                item.getTaxPercentage(),
                item.isTaxIncluded(),
                item.getQuantity(),
                unit.net(), unit.tax(), unit.gross(),
                line.net(), line.tax(), line.gross(),
                item.getNotes(),
                item.getCreatedAt());
    }
}
