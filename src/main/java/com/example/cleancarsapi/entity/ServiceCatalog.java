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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code service_catalog} row — an org's price-list entry for a service it offers
 * (e.g. "Oil Change"). Unique on {@code (org_id, name)}.
 *
 * <p>Only the <em>base</em> price and the GST inputs are stored. The net / GST /
 * gross breakdown is always derived in code ({@link com.example.cleancarsapi.dto.ServiceCatalogResponse#from})
 * and never persisted — there is no stored "final price".
 */
@Entity
@Table(name = "service_catalog")
@Getter
@Setter
@NoArgsConstructor
public class ServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false)
    private String name;

    /** Optional {@code service_categories} id. Null = uncategorized (also set null if the category is deleted). */
    private Long categoryId;

    /** Base price as entered by the org. Whether it already includes GST is {@link #gstIncluded}. */
    @Column(name = "default_price", nullable = false)
    private BigDecimal price;

    /** GST rate for this service, e.g. {@code 18.00}. Null = GST not applicable. */
    private BigDecimal gstPercentage;

    /** {@code true} = {@link #price} already includes GST; {@code false} = GST is added on top. */
    @Column(nullable = false)
    private boolean gstIncluded;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
