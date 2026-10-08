package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Size;
import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;
/**
 * Create/update payload for a service order. {@code orgId} and {@code createdBy}
 * come from the token; {@code customerId} is derived from the vehicle's owner.
 * Exactly one of {@code carId} / {@code bikeId} identifies the vehicle (validated
 * server-side). On update the vehicle is ignored — the order's car/bike is fixed.
 *
 * <p>Set {@code vendorId} to send the whole job to an outside garage — that alone
 * marks the order outsourced. {@code paymentPlan} null → ONE_TIME on create, unchanged
 * on update. {@code payments} null leaves the order's payments untouched (they can also be
 * recorded one at a time through {@code /payments}); a list — even empty — replaces them all,
 * keeping those whose {@code id} matches, under the plan's rules (see {@code ServiceOrderPaymentLedger}).
 * {@code status} null → IN_PROGRESS on create, unchanged on update. {@code items}
 * replaces the whole line set on update.
 */
public record ServiceOrderRequest(
        UUID carId,
        UUID bikeId,
        UUID employeeId,
        @PositiveOrZero @Max(DistanceLimits.MAX_METERS) Integer odometerReading,
        UUID vendorId,
        ServiceOrderStatus status,
        PaymentPlan paymentPlan,
        List<@Valid @NotNull ServiceOrderPaymentLine> payments,
        @Size(max = FieldLimits.NOTES_MAX) String notes,
        List<@Valid @NotNull ServiceOrderItemRequest> items
) {
    /** Everything except the fixed identifiers and status (which needs transition logic). */
    public void applyTo(ServiceOrder order) {
        order.setEmployeeId(employeeId);
        order.setOdometerReading(odometerReading);
        order.setVendorId(vendorId);
        order.setNotes(notes);
    }

    public List<ServiceOrderItemRequest> safeItems() {
        return items == null ? List.of() : items;
    }
}
