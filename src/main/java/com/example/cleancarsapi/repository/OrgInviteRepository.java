package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.InviteStatus;
import com.example.cleancarsapi.entity.OrgInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgInviteRepository extends JpaRepository<OrgInvite, UUID> {

    /** The org's sent invites (append-only history, newest first). */
    List<OrgInvite> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    /** Duplicate guard for sending: one pending invite per (org, email). */
    boolean existsByOrgIdAndEmailAndStatus(UUID orgId, String email, InviteStatus status);

    /** Duplicate guard for sending: one pending invite per (org, employee). */
    boolean existsByOrgIdAndEmployeeIdAndStatus(UUID orgId, UUID employeeId, InviteStatus status);

    /** Org-scoped fetch for sender-side actions (revoke). */
    Optional<OrgInvite> findByIdAndOrgId(UUID id, UUID orgId);

    /** Invitee-side: pending invites addressed to this email. */
    List<OrgInvite> findByEmailAndStatus(String email, InviteStatus status);
}
