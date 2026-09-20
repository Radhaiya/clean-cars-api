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

import java.time.LocalDateTime;

/**
 * {@code subscription_plans} row — the global plan catalogue (not org-scoped),
 * read-only from the app. Nullable numeric fields mean "unlimited".
 *
 * <p>Prices live in Razorpay: each billing cycle has its own Razorpay Plan ID
 * (nullable = cycle not offered); amounts are fetched live at the API boundary
 * (see {@code PlanService} / {@code RazorpayGateway}), never stored here. The
 * Trial plan has both IDs null — it is never sold.
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

    /** Razorpay Plan ID for the monthly cycle; null = monthly not offered. */
    @Column(name = "razorpay_monthly_plan_id", unique = true)
    private String razorpayMonthlyPlanId;

    /** Razorpay Plan ID for the yearly cycle; null = yearly not offered. */
    @Column(name = "razorpay_yearly_plan_id", unique = true)
    private String razorpayYearlyPlanId;

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
