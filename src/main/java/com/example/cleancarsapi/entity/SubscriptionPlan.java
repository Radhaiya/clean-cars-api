package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code subscription_plans} row — the global plan catalogue (not org-scoped),
 * read-only from the app. Nullable numeric fields mean "unlimited".
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** TODO(cashfree): manual INR figures until Cashfree plans exist; then source from Cashfree. */
    @Column(nullable = false)
    private BigDecimal monthlyPrice;

    /** null = yearly not offered (the pricing-page toggle hides/disables yearly). */
    private BigDecimal yearlyPrice;

    /** True for exactly one row: the dedicated, one-time-usable Trial plan. */
    @Column(name = "is_trial", nullable = false)
    private boolean isTrial;

    /** null = unlimited. */
    private Integer maxUsers;

    /** null = unlimited. */
    private Integer maxCars;

    /** null = unlimited. */
    private Integer reportWindowMonths;

    /** null = unlimited; 0 = statistics page hidden. */
    private Integer statsRangeYears;

    @Column(nullable = false)
    private boolean invoiceGeneration;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private int sortOrder;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
