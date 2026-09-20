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
 * {@code expenses} row — an org's own expense entry against a category name
 * (e.g. "Salary"). The category name is denormalized onto the row on purpose:
 * deleting the {@code expense_categories} label only removes it from the
 * dropdown, historical expenses keep their name. Many rows may share a label
 * (ten "Salary" entries a day are ten rows).
 *
 * <p>Only the <em>unit</em> amount and the GST inputs are stored. Net / GST / gross
 * (unit and line, i.e. x {@link #quantity}) are always derived in code
 * ({@link com.example.cleancarsapi.dto.ExpenseResponse#from}) and never persisted.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orgId;

    /** Denormalized label (no FK); validated against the org's {@code expense_categories} on create. */
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    /** Unit amount as entered by the org. Whether it already includes GST is {@link #gstIncluded}. */
    @Column(nullable = false)
    private BigDecimal amount;

    /** GST rate for this expense, e.g. {@code 18.00}. Null = GST not applicable. */
    private BigDecimal gstPercentage;

    /** {@code true} = {@link #amount} already includes GST; {@code false} = GST is added on top. */
    @Column(nullable = false)
    private boolean gstIncluded;

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
