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
 * {@code employees} row — a staff member ("worker") of the caller's organization.
 * The plan's {@code max_users} caps this roster (see {@code EmployeeCreateService});
 * an accepted invite links the user account via {@code userId} (migration 005 —
 * employees are the seat, docs/FEATURE-INVITES.md). {@code email} is the invite
 * address, nullable until the owner wants this person inside the module.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    /** Invite address; also {@code UNIQUE(org_id, email)} in the DB (NULLs allowed). */
    private String email;

    /** The accepted invitee's {@code users.id}; null until someone accepts the invite. */
    @Column(name = "user_id", unique = true)
    private UUID userId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Seat taken: an accepted invitee adopted this roster row (see InviteService.accept). */
    public void linkUser(UUID userId) {
        this.userId = userId;
    }
}
