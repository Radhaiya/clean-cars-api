package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code users} row. A user belongs to exactly 0 or 1 organization
 * ({@code orgId} is null until an invite is accepted). Sign-in is Firebase Auth only
 * (see {@code FirebaseIdTokenService}) — Google, Apple, and phone OTP all arrive as a
 * Firebase ID token, so there is no password. {@code firebaseUid} is the real identity
 * key; {@code email} is nullable because phone-only sign-ins have none.
 * <p>Column names are derived from the field names (camelCase &rarr; snake_case).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orgId;

    private String name;

    private String email;

    private String phone;

    private String firebaseUid;

    private UserRole role;

    private String status;

    /** The account's one-time free trial has been consumed (see SubscriptionService.startTrial). */
    @Column(nullable = false)
    private boolean trialUsed;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "active".equals(status);
    }

    public void markTrialUsed() {
        this.trialUsed = true;
    }

    /** Link this (previously org-less) user to the org it just created, as its owner. */
    public void assignToOrgAsOwner(long orgId) {
        this.orgId = orgId;
        this.role = UserRole.OWNER;
    }

    /** One-time bridge: attach a verified Firebase identity to a row found by email. */
    public void linkFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    /**
     * First-time Firebase sign-in (any provider — Google, Apple, phone OTP) with no
     * matching row: an org-less, already-active account (Firebase already verified the
     * identity, so there is no separate invite/activation step). {@code email} /
     * {@code phone} may each be null depending on which provider was used.
     */
    public static User provisionFromFirebase(String firebaseUid, String email, String phone, String name) {
        User user = new User();
        user.firebaseUid = firebaseUid;
        user.email = email;
        user.phone = phone;
        user.name = name != null ? name : (email != null ? email : (phone != null ? phone : "New user"));
        user.role = UserRole.STAFF;
        user.status = "active";
        return user;
    }
}
