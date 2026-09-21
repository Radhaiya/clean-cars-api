package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code razorpay_payments} row — a snapshot of what Razorpay actually charged
 * (Razorpay owns pricing; this records the transaction: amount, state, when).
 * Named {@code razorpay_payments} because {@code payments} is the garage's own
 * service-order receipts table.
 */
@Entity
@Table(name = "razorpay_payments")
@Getter
@Setter
@NoArgsConstructor
public class RazorpayPayment {

    @Id
    @UuidGenerator
    private UUID id;

    /** The local {@code subscriptions.id} the charge belongs to. */
    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(name = "razorpay_payment_id", nullable = false, unique = true)
    private String razorpayPaymentId;

    private String razorpayOrderId;

    private String razorpayInvoiceId;

    /** Razorpay raw amount (paise). */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private RazorpayPaymentStatus status;

    private java.time.LocalDateTime paidAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
