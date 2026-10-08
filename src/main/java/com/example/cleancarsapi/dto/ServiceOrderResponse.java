package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * Full read projection for a service order — the order, its lines, and the computed totals.
 * {@code amountPaid} / {@code amountRemaining} come from its payments (remaining = gross total
 * minus received, never negative); {@code paid} is true once the order is fully covered.
 */
public record ServiceOrderResponse(
        UUID id,
        UUID carId,
        String carNumber,
        UUID bikeId,
        String bikeNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        boolean isCustomerDeleted,
        ServiceOrderVehicle vehicle,
        UUID createdBy,
        UUID employeeId,
        String employeeName,
        Integer odometerReading,
        boolean outsourced,
        UUID vendorId,
        String vendorName,
        ServiceOrderStatus status,
        PaymentPlan paymentPlan,
        boolean paid,
        BigDecimal amountPaid,
        BigDecimal amountRemaining,
        List<PaymentResponse> payments,
        String notes,
        List<ServiceOrderItemResponse> items,
        BigDecimal netTotal,
        BigDecimal taxTotal,
        BigDecimal grossTotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        Amc amc
) {
    /** Present on an AMC redemption: which AMC, and the use it took (1-based) out of {@code totalSlots}. */
    public record Amc(UUID subscriptionId, String planName, int useNumber, int totalSlots) {
    }

    public static ServiceOrderResponse of(ServiceOrder o,
                                          String carNumber,
                                          String bikeNumber,
                                          String customerName,
                                          String customerPhone,
                                          boolean isCustomerDeleted,
                                          ServiceOrderVehicle vehicle,
                                          String employeeName,
                                          String vendorName,
                                          List<ServiceOrderItem> items,
                                          List<Payment> payments,
                                          Amc amc) {
        BigDecimal paid = o.getAmountPaid() == null ? BigDecimal.ZERO : o.getAmountPaid();
        List<ServiceOrderItemResponse> lines = items.stream().map(ServiceOrderItemResponse::from).toList();
        TaxBreakdown total = items.stream()
                .map(i -> TaxBreakdown.ofLine(i))
                .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
        return new ServiceOrderResponse(
                o.getId(),
                o.getCarId(), carNumber,
                o.getBikeId(), bikeNumber,
                o.getCustomerId(), customerName, customerPhone, isCustomerDeleted,
                vehicle,
                o.getCreatedBy(),
                o.getEmployeeId(), employeeName,
                o.getOdometerReading(),
                o.isOutsourced(),
                o.getVendorId(), vendorName,
                o.getStatus(),
                o.getPaymentPlan(), o.isPaid(), paid,
                total.gross().subtract(paid).max(BigDecimal.ZERO),
                PaymentResponse.listOf(payments, total.gross()),
                o.getNotes(),
                lines,
                total.net(), total.tax(), total.gross(),
                o.getCreatedAt(), o.getUpdatedAt(), o.getCompletedAt(), amc);
    }
}
