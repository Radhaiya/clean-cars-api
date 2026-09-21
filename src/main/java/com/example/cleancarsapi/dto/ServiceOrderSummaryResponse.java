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
/** Lightweight row for the service-order list — names resolved, gross total only, no line detail. */
public record ServiceOrderSummaryResponse(
        UUID id,
        UUID carId,
        String carNumber,
        UUID customerId,
        String customerName,
        UUID employeeId,
        String employeeName,
        ServiceOrderStatus status,
        boolean paid,
        LocalDate paymentDate,
        PaymentType paymentType,
        boolean outsourced,
        UUID vendorId,
        String vendorName,
        int itemCount,
        BigDecimal grossTotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ServiceOrderSummaryResponse of(ServiceOrder o,
                                                 String carNumber,
                                                 String customerName,
                                                 String employeeName,
                                                 String vendorName,
                                                 List<ServiceOrderItem> items) {
        GstBreakdown total = items.stream()
                .map(i -> GstBreakdown.of(i.getBasePrice(), i.getGstPercentage(), i.isGstIncluded()).times(i.getQuantity()))
                .reduce(GstBreakdown.zero(), GstBreakdown::plus);
        return new ServiceOrderSummaryResponse(
                o.getId(),
                o.getCarId(), carNumber,
                o.getCustomerId(), customerName,
                o.getEmployeeId(), employeeName,
                o.getStatus(),
                o.isPaid(), o.getPaymentDate(), o.getPaymentType(),
                o.isOutsourced(), o.getVendorId(), vendorName,
                items.size(), total.gross(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
