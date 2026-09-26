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
 * {@code expenses} row — an org's own expense entry against a category name
 * (e.g. "Salary"). The category name is denormalized onto the row on purpose:
 * deleting the {@code expense_categories} label only removes it from the
 * dropdown, historical expenses keep their name. Many rows may share a label
 * (ten "Salary" entries a day are ten rows).
 *
 * <p>Only the <em>unit</em> amount and the tax inputs are stored. Net / tax / gross
 * (unit and line, i.e. x {@link #quantity}) are always derived in code
 * ({@link com.example.cleancarsapi.dto.ExpenseResponse#from}) and never persisted.
 *
 * <p>The tax columns keep their historical {@code gst_*} names in the DB — renamed
 * only at the Java/JSON boundary.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    /** Denormalized label (no FK); validated against the org's {@code expense_categories} on create. */
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    /** Unit amount as entered by the org. Whether it already includes tax is {@link #taxIncluded}. */
    @Column(nullable = false)
    private BigDecimal amount;

    /** Tax rate for this expense, e.g. {@code 18.00}. Null = tax not applicable. */
    @Column(name = "gst_percentage")
    private BigDecimal taxPercentage;

    /** {@code true} = {@link #amount} already includes tax; {@code false} = tax is added on top. */
    @Column(name = "gst_included", nullable = false)
    private boolean taxIncluded;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Owned by the DB ({@code ON UPDATE CURRENT_TIMESTAMP}); read back but never written by the app. */
    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
