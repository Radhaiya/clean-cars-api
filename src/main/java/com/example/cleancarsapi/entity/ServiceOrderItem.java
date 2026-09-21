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
 * {@code basePrice} / {@code gstPercentage} / {@code gstIncluded} are freely
 * editable per line. Net / GST / gross are computed in code, never stored.
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

    private BigDecimal gstPercentage;

    @Column(nullable = false)
    private boolean gstIncluded;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
