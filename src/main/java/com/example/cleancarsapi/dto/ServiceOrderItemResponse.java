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
        BigDecimal gstPercentage,
        boolean gstIncluded,
        int quantity,
        BigDecimal unitNet,
        BigDecimal unitGst,
        BigDecimal unitGross,
        BigDecimal lineNet,
        BigDecimal lineGst,
        BigDecimal lineGross,
        String notes,
        LocalDateTime createdAt
) {
    public static ServiceOrderItemResponse from(ServiceOrderItem item) {
        GstBreakdown unit = GstBreakdown.of(item.getBasePrice(), item.getGstPercentage(), item.isGstIncluded());
        GstBreakdown line = unit.times(item.getQuantity());
        return new ServiceOrderItemResponse(
                item.getId(),
                item.getServiceName(),
                item.getBasePrice().setScale(2, RoundingMode.HALF_UP),
                item.getGstPercentage(),
                item.isGstIncluded(),
                item.getQuantity(),
                unit.net(), unit.gst(), unit.gross(),
                line.net(), line.gst(), line.gross(),
                item.getNotes(),
                item.getCreatedAt());
    }
}
