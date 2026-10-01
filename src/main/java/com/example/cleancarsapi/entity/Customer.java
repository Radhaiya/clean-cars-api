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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code customers} row. Always scoped to an organization — {@code orgId} is set
 * once on creation from the caller's token and never changes.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    /** Null once the customer is deleted — frees the number for a new customer. */
    private String phone;

    private String altPhone;

    private String email;

    private String address;

    @Column(columnDefinition = "text")
    private String notes;

    /** Soft-delete flag — the row stays so its service orders keep resolving. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Soft delete: keep only the name (service orders still show it), wipe every contact field. */
    public void softDelete() {
        this.deleted = true;
        this.phone = null;
        this.altPhone = null;
        this.email = null;
        this.address = null;
        this.notes = null;
    }
}
