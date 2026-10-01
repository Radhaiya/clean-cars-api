package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/** Lightweight row for the service-order list — names resolved, gross total and payment progress, no line detail. */
public record ServiceOrderSummaryResponse(
        UUID id,
        UUID carId,
        String carNumber,
        UUID bikeId,
        String bikeNumber,
        UUID customerId,
        String customerName,
        boolean isCustomerDeleted,
        boolean isVehicleDeleted,
        UUID employeeId,
        String employeeName,
        ServiceOrderStatus status,
        PaymentPlan paymentPlan,
        boolean paid,
        BigDecimal amountPaid,
        BigDecimal amountRemaining,
        boolean outsourced,
        UUID vendorId,
        String vendorName,
        int itemCount,
        BigDecimal grossTotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isAmc
) {
    public static ServiceOrderSummaryResponse of(ServiceOrder o,
                                                 String carNumber,
                                                 String bikeNumber,
                                                 String customerName,
                                                 boolean isCustomerDeleted,
                                                 boolean isVehicleDeleted,
                                                 String employeeName,
                                                 String vendorName,
                                                 List<ServiceOrderItem> items) {
        TaxBreakdown total = items.stream()
                .map(i -> TaxBreakdown.ofLine(i))
                .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
        return new ServiceOrderSummaryResponse(
                o.getId(),
                o.getCarId(), carNumber,
                o.getBikeId(), bikeNumber,
                o.getCustomerId(), customerName, isCustomerDeleted, isVehicleDeleted,
                o.getEmployeeId(), employeeName,
                o.getStatus(),
                o.getPaymentPlan(), o.isPaid(), o.getAmountPaid(),
                total.gross().subtract(o.getAmountPaid()).max(BigDecimal.ZERO),
                o.isOutsourced(), o.getVendorId(), vendorName,
                items.size(), total.gross(),
                o.getCreatedAt(), o.getUpdatedAt(), o.isAmcRedemption());
    }
}
