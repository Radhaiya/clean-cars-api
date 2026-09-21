package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code subscriptions} row — an org's contract with a plan. Append-only history:
 * an org may accumulate several rows over time (trial lapses, resubscribe, plan
 * change), but at most one is "live" ({@code TRIALING} / {@code ACTIVE} /
 * {@code PAST_DUE}) at once.
 *
 * <p>For a trial, {@code startDate}/{@code endDate} hold the trial window. A paid
 * subscription carries {@code razorpaySubscriptionId}; its {@link SubscriptionStatus}
 * is synced from Razorpay webhooks (PENDING → ACTIVE → PAST_DUE/HALTED/…), and
 * the actual price charged is never stored here — that lives on the Razorpay payment
 * snapshots ({@code razorpay_payments}).
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private SubscriptionStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private String paymentReference;

    /** Razorpay subscription reference (paid rows); null while trialing. */
    @Column(name = "razorpay_subscription_id", unique = true)
    private String razorpaySubscriptionId;

    /** The billing cycle sold (MONTHLY/YEARLY) — the chosen Razorpay plan encodes it; null while trialing. */
    private BillingCycle billingCycle;

    /**
     * The autopay method the Razorpay subscription runs on (e.g. {@code card}, {@code upi}),
     * snapshot from the activation webhook. UPI mandates can be silently mutated
     * ("subscriptions cannot be updated when payment mode is upi") — plan changes need this.
     */
    private String paymentMethod;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
