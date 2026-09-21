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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code expense_categories} row — an org's own grouping for its expenses
 * (e.g. "Utilities", "Rent", "Supplies"). Not a shared master list. Unique on {@code (org_id, name)}.
 */
@Entity
@Table(name = "expense_categories")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseCategory {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
