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
 * {@code invoices} row — the printable tax invoice of one service order (at most one per
 * order). Lines, totals and payments are read live from the order; this row only keeps what
 * the order lacks: the per-org {@code invoiceNumber}, the issue date and the next-service hints.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false, updatable = false)
    private UUID serviceOrderId;

    /** Running sequence per org, starting at 1; assigned once. */
    @Column(nullable = false, updatable = false)
    private int invoiceNumber;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate nextServiceDate;

    /** Odometer reading at which the next service is due. */
    private Integer nextServiceKm;

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
