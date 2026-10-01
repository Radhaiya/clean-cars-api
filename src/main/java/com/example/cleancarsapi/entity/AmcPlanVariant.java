package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code amc_plan_variants} row — a sellable tenure + frequency of an {@link AmcPlan}. Tenure is
 * stored in months and must be a multiple of {@code intervalMonths} (1 = monthly, 12 = yearly…);
 * {@code totalSlots = tenureMonths / intervalMonths}. Prices are per service row ({@link AmcVariantRow}).
 */
@Entity
@Table(name = "amc_plan_variants")
@Getter
@Setter
@NoArgsConstructor
public class AmcPlanVariant {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private int tenureMonths;

    @Column(nullable = false)
    private int intervalMonths;

    @Column(nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public int totalSlots() {
        return tenureMonths / intervalMonths;
    }
}
