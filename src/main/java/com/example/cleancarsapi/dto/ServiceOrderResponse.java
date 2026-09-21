package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Full read projection for a service order — the order, its lines, and the computed totals. */
public record ServiceOrderResponse(
        UUID id,
        UUID carId,
        String carNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        UUID createdBy,
        UUID employeeId,
        String employeeName,
        Integer odometerReading,
        boolean outsourced,
        UUID vendorId,
        String vendorName,
        ServiceOrderStatus status,
        boolean paid,
        LocalDate paymentDate,
        PaymentType paymentType,
        String notes,
        List<ServiceOrderItemResponse> items,
        BigDecimal netTotal,
        BigDecimal gstTotal,
        BigDecimal grossTotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
    public static ServiceOrderResponse of(ServiceOrder o,
                                          String carNumber,
                                          String customerName,
                                          String customerPhone,
                                          String employeeName,
                                          String vendorName,
                                          List<ServiceOrderItem> items) {
        List<ServiceOrderItemResponse> lines = items.stream().map(ServiceOrderItemResponse::from).toList();
        GstBreakdown total = items.stream()
                .map(i -> GstBreakdown.of(i.getBasePrice(), i.getGstPercentage(), i.isGstIncluded()).times(i.getQuantity()))
                .reduce(GstBreakdown.zero(), GstBreakdown::plus);
        return new ServiceOrderResponse(
                o.getId(),
                o.getCarId(), carNumber,
                o.getCustomerId(), customerName, customerPhone,
                o.getCreatedBy(),
                o.getEmployeeId(), employeeName,
                o.getOdometerReading(),
                o.isOutsourced(),
                o.getVendorId(), vendorName,
                o.getStatus(),
                o.isPaid(), o.getPaymentDate(), o.getPaymentType(),
                o.getNotes(),
                lines,
                total.net(), total.gst(), total.gross(),
                o.getCreatedAt(), o.getUpdatedAt(), o.getCompletedAt());
    }
}
