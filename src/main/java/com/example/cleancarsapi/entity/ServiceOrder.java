package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code service_orders} row — one job card per vehicle visit ("the service log").
 *
 * <p>Every order belongs to exactly one vehicle — a car or a bike through
 * {@code carId} / {@code bikeId} (mutually exclusive, enforced app-side; fixed
 * once set). {@code customerId} is that vehicle's owner, snapshotted here and
 * fixed once set. The order total is never stored — it is summed from
 * {@code service_order_items} on read. Money received lives in {@code payments}
 * (see {@link Payment}); {@code amountPaid} and {@code paid} are denormalized from
 * it by {@code ServiceOrderPaymentLedger} — never set them directly.
 */
@Entity
@Table(name = "service_orders")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOrder {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    /** The vehicle under service — exactly one of {@code carId} / {@code bikeId} is set. */
    @Column(updatable = false)
    private UUID carId;

    /** The vehicle under service — exactly one of {@code carId} / {@code bikeId} is set. */
    @Column(updatable = false)
    private UUID bikeId;

    @Column(nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    /** {@code employees.id} of the staff member on the job (optional). */
    private UUID employeeId;

    private Integer odometerReading;

    /** The outside garage the whole job is sent to; a non-null value means the order is outsourced. */
    private UUID vendorId;

    @Column(nullable = false)
    private ServiceOrderStatus status = ServiceOrderStatus.IN_PROGRESS;

    /** One full payment, or any number of partial ones — see {@link PaymentPlan}. */
    @Column(nullable = false)
    private PaymentPlan paymentPlan = PaymentPlan.ONE_TIME;

    /** Sum of this order's payments. Maintained by {@code ServiceOrderPaymentLedger}. */
    @Column(nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** Derived: received something and at least the order total. Maintained by {@code ServiceOrderPaymentLedger}. */
    @Column(nullable = false)
    private boolean paid;

    /** The AMC this order redeemed (null = a normal order). Fixed once set; such an order's lines are locked at a 100% discount. */
    @Column(updatable = false)
    private UUID amcSubscriptionId;

    /** The AMC period (0-based) this redemption took. */
    @Column(updatable = false)
    private Integer amcSlotIndex;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    public boolean isAmcRedemption() {
        return amcSubscriptionId != null;
    }

    /** Derived: an order is outsourced when it has a vendor. */
    public boolean isOutsourced() {
        return vendorId != null;
    }

    /** Move to a new status, stamping/clearing {@code completedAt} to match. */
    public void transitionTo(ServiceOrderStatus newStatus) {
        this.status = newStatus;
        if (newStatus == ServiceOrderStatus.COMPLETED) {
            if (completedAt == null) {
                this.completedAt = LocalDateTime.now();
            }
        } else {
            this.completedAt = null;
        }
    }
}
