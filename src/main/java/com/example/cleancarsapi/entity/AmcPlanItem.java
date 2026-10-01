package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** {@code amc_plan_items} row — one fixed service name in an {@link AmcPlan}'s bundle. Never edited after creation. */
@Entity
@Table(name = "amc_plan_items")
@Getter
@Setter
@NoArgsConstructor
public class AmcPlanItem {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false, updatable = false)
    private String serviceName;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;
}
