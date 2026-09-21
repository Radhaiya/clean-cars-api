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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code service_orders} row — one job card per car visit ("the service log").
 *
 * <p>{@code carId} / {@code customerId} are fixed once set ({@code customerId} is
 * the car's owner, snapshotted here). The order total is never stored — it is summed
 * from {@code service_order_items} on read. {@code paid} and {@code paymentDate} are
 * independent flags, each free to change without touching the other.
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

    @Column(nullable = false, updatable = false)
    private UUID carId;

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

    @Column(nullable = false)
    private boolean paid;

    private LocalDate paymentDate;

    /** How the order was paid (card / cash / UPI); null until recorded. */
    private PaymentType paymentType;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

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
