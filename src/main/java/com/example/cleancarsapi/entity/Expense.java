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
 * {@code expenses} row — an org's own expense entry (e.g. "Diesel for generator").
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

    @Column(nullable = false)
    private String title;

    /** Optional {@code expense_categories} id. Null = uncategorized (also set null if the category is deleted). */
    private Long categoryId;

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
}
