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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code payments} row — one receipt of money against a service order (the garage's own
 * receipts, not {@link RazorpayPayment}). A one-time order has a single full-amount row, a
 * split order has several; {@code service_orders.amount_paid} is their denormalized sum,
 * kept in step by {@code ServiceOrderPaymentLedger}. {@code paymentType} is null only for
 * legacy orders migrated from the old paid flag that never recorded a method.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false, updatable = false)
    private UUID serviceOrderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "mode")
    private PaymentType paymentType;

    @Column(nullable = false)
    private LocalDate paymentDate;

    /** {@code users.id} of whoever recorded it. */
    @Column(updatable = false)
    private UUID receivedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
