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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code amc_invoices} row — the printable invoice of one AMC sale (at most one per
 * {@link AmcSubscription}). {@code invoiceNumber} runs per org on its own sequence (printed
 * {@code AMC-0001}); plan, rows and prices are read live from the AMC.
 */
@Entity
@Table(name = "amc_invoices")
@Getter
@Setter
@NoArgsConstructor
public class AmcInvoice {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false, updatable = false)
    private UUID amcSubscriptionId;

    @Column(nullable = false, updatable = false)
    private int invoiceNumber;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
