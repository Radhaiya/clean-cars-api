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
 * {@code service_catalog} row — an org's price-list entry for a service it offers
 * (e.g. "Oil Change"). Unique on {@code (org_id, name)}.
 *
 * <p>Only the <em>base</em> price and the tax inputs are stored. The net / tax /
 * gross breakdown is always derived in code ({@link com.example.cleancarsapi.dto.ServiceCatalogResponse#from})
 * and never persisted — there is no stored "final price".
 *
 * <p>The tax columns keep their historical {@code gst_*} names in the DB — renamed
 * only at the Java/JSON boundary.
 */
@Entity
@Table(name = "service_catalog")
@Getter
@Setter
@NoArgsConstructor
public class ServiceCatalog {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    /** Optional {@code service_categories} id. Null = uncategorized (also set null if the category is deleted). */
    private UUID categoryId;

    /** Base price as entered by the org. Whether it already includes tax is {@link #taxIncluded}. */
    @Column(name = "default_price", nullable = false)
    private BigDecimal price;

    /** Tax rate for this service, e.g. {@code 18.00}. Null = tax not applicable. */
    @Column(name = "gst_percentage")
    private BigDecimal taxPercentage;

    /** {@code true} = {@link #price} already includes tax; {@code false} = tax is added on top. */
    @Column(name = "gst_included", nullable = false)
    private boolean taxIncluded;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
