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
 * {@code amc_plans} row — an AMC bundle template ("Gold AMC"). Its service names are fixed
 * ({@link AmcPlanItem}); tenure/frequency/prices live on its {@link AmcPlanVariant}s. Unique on
 * {@code (org_id, name)}. Archived plans cannot be sold; they are never hard-deleted once sold.
 */
@Entity
@Table(name = "amc_plans")
@Getter
@Setter
@NoArgsConstructor
public class AmcPlan {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
