package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code amc_subscription_items} row — a service of a sold AMC with the price / tax it was sold at
 * (a snapshot, no link back to the variant). Redemption lines are built from these.
 */
@Entity
@Table(name = "amc_subscription_items")
@Getter
@Setter
@NoArgsConstructor
public class AmcSubscriptionItem {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(nullable = false, updatable = false)
    private String serviceName;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private int quantity = 1;

    @Column(nullable = false, updatable = false)
    private BigDecimal price;

    @Column(updatable = false)
    private BigDecimal taxPercentage;

    @Column(nullable = false, updatable = false)
    private boolean taxIncluded;
}
