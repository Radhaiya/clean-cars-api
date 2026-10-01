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
 * {@code amc_subscriptions} row — one AMC sold to ONE car or bike (exactly one of {@code carId} /
 * {@code bikeId}; no customer — it follows the vehicle). Holds a snapshot of the variant it was sold
 * from ({@link AmcSubscriptionItem} rows + tenure/interval/plan name), so later template edits never
 * change it, and the single upfront payment (AMC revenue). Used / lapsed / remaining are never stored —
 * see {@code AmcSlots}. Immutable after sale: no cancellation or refund.
 */
@Entity
@Table(name = "amc_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class AmcSubscription {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(updatable = false)
    private UUID carId;

    @Column(updatable = false)
    private UUID bikeId;

    @Column(nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false, updatable = false)
    private UUID variantId;

    @Column(nullable = false, updatable = false)
    private String planName;

    @Column(nullable = false, updatable = false)
    private int tenureMonths;

    @Column(nullable = false, updatable = false)
    private int intervalMonths;

    @Column(nullable = false, updatable = false)
    private LocalDate startDate;

    /** Whole-tenure upfront price: Σ rows × total slots (tax per row). */
    @Column(nullable = false, updatable = false)
    private BigDecimal saleNet;

    @Column(nullable = false, updatable = false)
    private BigDecimal saleTax;

    @Column(nullable = false, updatable = false)
    private BigDecimal saleGross;

    @Column(name = "payment_mode", nullable = false, updatable = false)
    private PaymentType paymentType;

    @Column(nullable = false, updatable = false)
    private LocalDate paymentDate;

    /** {@code users.id} of whoever recorded the sale. */
    @Column(updatable = false)
    private UUID receivedBy;

    /** {@code employees.id} of the seller (optional). */
    @Column(updatable = false)
    private UUID soldByEmployeeId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public int totalSlots() {
        return tenureMonths / intervalMonths;
    }
}
