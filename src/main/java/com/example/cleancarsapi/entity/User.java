package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

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
    @UuidGenerator
    private UUID id;

    private UUID orgId;

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
    public void assignToOrgAsOwner(UUID orgId) {
        this.orgId = orgId;
        this.role = UserRole.OWNER;
    }

    /**
     * Join an org by accepting an invite: {@code orgId} set once with the role the
     * invite granted. See {@code InviteService.accept}.
     */
    public void acceptInvite(UUID orgId, UserRole role) {
        this.orgId = orgId;
        this.role = role;
    }

    /** Exit the org voluntarily (org-less again; the owner cannot — see UserService.leaveOrg). */
    public void leaveOrg() {
        this.orgId = null;
    }

    /**
     * True when the user is a managed member of an org — joined via an invite they
     * accepted. The org creator holds {@link UserRole#OWNER}, everyone else in an
     * org was invited (MANAGER / WORKER, or legacy values). Used by the UI through
     * {@code GET /api/me}'s {@code isManaged} flag.
     */
    public boolean isManagedMember() {
        return orgId != null && role != UserRole.OWNER;
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
