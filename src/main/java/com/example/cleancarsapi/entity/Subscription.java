package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code subscriptions} row — an org's contract with a plan. Append-only history:
 * an org may accumulate several rows over time (trial lapses, resubscribe, plan
 * change), but at most one is "live" ({@code TRIALING} / {@code ACTIVE} /
 * {@code PAST_DUE}) at once.
 *
 * <p>For a trial, {@code startDate}/{@code endDate} hold the trial window. Cashfree
 * fields (cashfree_subscription_id, billing_cycle, unit_price, current_period_*)
 * are added when the paid flow is wired.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private SubscriptionStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private String paymentReference;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
