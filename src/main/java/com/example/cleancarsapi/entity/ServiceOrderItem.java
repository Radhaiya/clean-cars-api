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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code service_order_items} row — a snapshot line on a service order. Seeded from
 * a {@link ServiceCatalog} entry at add-time but with no link back to it;
 * {@code basePrice} / {@code taxPercentage} / {@code taxIncluded} are freely
 * editable per line. Net / tax / gross are computed in code, never stored.
 */
@Entity
@Table(name = "service_order_items")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOrderItem {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID serviceOrderId;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private BigDecimal basePrice;

    /** Tax rate for this line. Null = tax not applicable. DB column keeps its historical name. */
    @Column(name = "gst_percentage")
    private BigDecimal taxPercentage;
    /** {@code true} = {@link #basePrice} already includes tax; {@code false} = tax is added on top. */
    @Column(name = "gst_included", nullable = false)
    private boolean taxIncluded;

    @Column(nullable = false)
    private int quantity = 1;

    /** Discount per unit, taken off {@link #basePrice} before tax (0 ≤ amount ≤ basePrice). The percent is derived on read, never stored. */
    @Column(nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
