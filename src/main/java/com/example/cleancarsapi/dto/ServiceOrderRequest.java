package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Create/update payload for a service order. {@code orgId} and {@code createdBy}
 * come from the token; {@code customerId} is derived from the car's owner. On
 * update the {@code carId} is ignored (an order's car is fixed).
 *
 * <p>Set {@code vendorId} to send the whole job to an outside garage — that alone
 * marks the order outsourced. {@code paid} defaults to false when omitted.
 * {@code status} null → IN_PROGRESS on create, unchanged on update. {@code items}
 * replaces the whole line set on update.
 */
public record ServiceOrderRequest(
        @NotNull Long carId,
        Long employeeId,
        Integer odometerReading,
        Long vendorId,
        ServiceOrderStatus status,
        Boolean paid,
        LocalDate paymentDate,
        PaymentType paymentType,
        String notes,
        @Valid List<ServiceOrderItemRequest> items
) {
    public boolean isPaid() {
        return Boolean.TRUE.equals(paid);
    }

    /** Everything except the fixed identifiers and status (which needs transition logic). */
    public void applyTo(ServiceOrder order) {
        order.setEmployeeId(employeeId);
        order.setOdometerReading(odometerReading);
        order.setVendorId(vendorId);
        order.setPaid(isPaid());
        order.setPaymentDate(paymentDate);
        order.setPaymentType(paymentType);
        order.setNotes(notes);
    }

    public List<ServiceOrderItemRequest> safeItems() {
        return items == null ? List.of() : items;
    }
}
