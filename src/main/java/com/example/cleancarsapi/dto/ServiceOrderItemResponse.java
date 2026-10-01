package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * Read projection for a service-order line. {@code unit*} is the per-unit
 * breakdown (after the per-unit discount); {@code line*} is {@code unit* x quantity}.
 * {@code discountPercent} is derived from the stored {@code discountAmount}. All derived on read.
 */
public record ServiceOrderItemResponse(
        UUID id,
        String serviceName,
        BigDecimal basePrice,
        BigDecimal taxPercentage,
        boolean taxIncluded,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal discountPercent,
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
        BigDecimal discount = item.getDiscountAmount() == null ? BigDecimal.ZERO : item.getDiscountAmount();
        TaxBreakdown unit = TaxBreakdown.of(TaxBreakdown.afterDiscount(item.getBasePrice(), discount),
                item.getTaxPercentage(), item.isTaxIncluded());
        TaxBreakdown line = unit.times(item.getQuantity());
        return new ServiceOrderItemResponse(
                item.getId(),
                item.getServiceName(),
                item.getBasePrice().setScale(2, RoundingMode.HALF_UP),
                item.getTaxPercentage(),
                item.isTaxIncluded(),
                item.getQuantity(),
                discount.setScale(2, RoundingMode.HALF_UP),
                percentOf(discount, item.getBasePrice()),
                unit.net(), unit.tax(), unit.gross(),
                line.net(), line.tax(), line.gross(),
                item.getNotes(),
                item.getCreatedAt());
    }

    private static BigDecimal percentOf(BigDecimal discount, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return discount.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
