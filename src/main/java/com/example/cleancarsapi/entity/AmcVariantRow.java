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
 * {@code amc_variant_rows} row — the price and tax of one service ({@link AmcPlanItem}) within a
 * variant. Tax is per row, with the same semantics as service-catalog lines: {@code price} is the
 * gross when {@code taxIncluded}, otherwise the net. There is no flat AMC price.
 */
@Entity
@Table(name = "amc_variant_rows")
@Getter
@Setter
@NoArgsConstructor
public class AmcVariantRow {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID variantId;

    @Column(nullable = false, updatable = false)
    private UUID planItemId;

    /** Units of this service per use (like a service-order line); the row is {@code price × quantity}. */
    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private BigDecimal price;

    /** Tax rate, e.g. {@code 18.00}. Null = tax not applicable. */
    private BigDecimal taxPercentage;

    @Column(nullable = false)
    private boolean taxIncluded;
}
