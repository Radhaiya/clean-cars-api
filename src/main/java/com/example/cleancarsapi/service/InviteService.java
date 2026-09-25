package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AcceptInviteResponse;
import com.example.cleancarsapi.dto.InviteRequest;
import com.example.cleancarsapi.dto.InviteResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.InviteStatus;
import com.example.cleancarsapi.entity.OrgInvite;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.OrgInviteRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.JwtService;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import com.example.cleancarsapi.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Org invites (workflow resource, not the CRUD-four split — same precedent as
 * {@code SubscriptionService}).
 *
 * <p><strong>The employee is the seat.</strong> An invite is addressed to one
 * {@code employees} roster row whose {@code email} is the address; the plan's
 * {@code maxUsers} caps that roster ({@code EmployeeCreateService} checks it on
 * create; the re-check here covers employees added/edited between send and
 * accept). The invitee, after signing in, accepts — {@code users.org_id} gains
 * the org and {@code employees.user_id} links the account — or declines.
 *
 * <p><strong>Sender side (owner only)</strong> — {@link #create}, {@link #list},
 * {@link #revoke}. <strong>Invitee side</strong> — {@link #myInvites}, {@link #accept},
 * {@link #decline}. There is no email transport; the in-app inbox is the delivery.
 * Rules/codes: docs/FEATURE-INVITES.md.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InviteService {

    /** How long a pending invite stays acceptable. */
    static final int INVITE_TTL_DAYS = 7;

    private final UserRepository users;
    private final EmployeeRepository employees;
    private final OrganizationRepository organizations;
    private final OrgInviteRepository invites;
    private final PlanLimitService planLimits;
    private final JwtService jwtService;

    /** Owner invites one of his employees (by the email on that row). Owner-only. */
    @Transactional
    public InviteResponse create(InviteRequest request) {
        AuthenticatedUser me = AuthContext.require(UserRole.OWNER);
        UUID orgId = me.requireOrgId();

        if (!UserRole.isInvitable(request.role())) {
            throw new ConflictException("invite_invalid_role",
                    "Invites can only grant the manager or worker role");
        }

        Employee employee = employees.findByIdAndOrgId(request.employeeId(), orgId)
                .orElseThrow(() -> new NotFoundException("employee", request.employeeId()));
        if (employee.getEmail() == null) {
            throw new BadRequestException(
                    "Employee has no email on file — add one before inviting them");
        }
        String email = normalize(employee.getEmail());

        requireOrgSubscribable(orgId);
        assertSeatsAvailable(orgId);

        // Never address an occupied seat — a member of any org cannot be invited.
        users.findByEmailIgnoreCase(email)
                .filter(u -> u.getOrgId() != null)
                .ifPresent(u -> { throw ConflictException.userAlreadyInOrg(email); });
        if (employee.getUserId() != null) {
            throw ConflictException.employeeAlreadyLinked();
        }
        if (invites.existsByOrgIdAndEmployeeIdAndStatus(orgId, employee.getId(), InviteStatus.PENDING)) {
            throw ConflictException.inviteAlreadyPending(email);
        }

   LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(INVITE_TTL_DAYS);
        OrgInvite invite = invites.save(OrgInvite.create(
                orgId, me.userId(), employee.getId(), email, request.role(),
                UUID.randomUUID().toString(), now, expiresAt));

        log.info("Invite sent: org={} employee={} email={} role={} invite={} expires={}",
                orgId, employee.getId(), email, request.role(), invite.getId(), expiresAt);
        return InviteResponse.from(invite, employee, null, me.name());
    }

    /** The org's sent invites (append-only history, newest first). Owner-only. */
    @Transactional(readOnly = true)
    public List<InviteResponse> list() {
        UUID orgId = AuthContext.require(UserRole.OWNER).requireOrgId();
        return invites.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(invite -> InviteResponse.from(invite, employeeOf(invite), null, null))
                .toList();
    }

    /** Cancel a pending invite. Org-scoped; only pending rows revoke. Owner-only. */
    @Transactional
    public void revoke(UUID inviteId) {
        UUID orgId = AuthContext.require(UserRole.OWNER).requireOrgId();
        OrgInvite invite = invites.findByIdAndOrgId(inviteId, orgId)
                .orElseThrow(() -> new NotFoundException("invite", inviteId));
        if (!invite.isPending()) {
            throw ConflictException.inviteNotPending();
        }
        invite.revoke();
        log.info("Invite revoked: org={} employee={} email={} invite={}",
                orgId, invite.getEmployeeId(), invite.getEmail(), inviteId);
    }

    /** Pending, unexpired invites addressed to the caller's email. Empty for phone-only accounts. */
    @Transactional(readOnly = true)
    public List<InviteResponse> myInvites() {
        AuthenticatedUser me = AuthContext.require();
        User user = users.findById(me.userId())
                .orElseThrow(() -> new NotFoundException("user", me.userId()));
        if (user.getEmail() == null || user.getOrgId() != null) {
            // Phone-only accounts have no address to match, and an org member
            // cannot receive invites (one account : one org).
            return List.of();
        }
        String email = normalize(user.getEmail());
        return invites.findByEmailAndStatus(email, InviteStatus.PENDING).stream()
                .filter(invite -> !invite.isExpired(LocalDateTime.now()))
                .map(invite -> InviteResponse.from(invite, employeeOf(invite),
                        orgName(invite.getOrgId()), inviterName(invite)))
                .toList();
    }

    /** Accept: caller joins the org with the invite's role and takes over the employee seat. */
    @Transactional
    public AcceptInviteResponse accept(UUID inviteId) {
        AuthenticatedUser me = AuthContext.require();
        // Row-lock: two concurrent actions on the same user (accept + leave, double accept).
        User user = users.findByIdForUpdate(me.userId())
                .orElseThrow(() -> new NotFoundException("user", me.userId()));

        OrgInvite invite = invites.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("invite", inviteId));
        if (user.getEmail() == null) {
            throw ConflictException.inviteNoEmail();
        }
        if (user.getOrgId() != null) {
            throw ConflictException.userAlreadyHasOrg();
        }
        if (!normalize(user.getEmail()).equals(invite.getEmail())) {
            throw ConflictException.inviteEmailMismatch();
        }
        if (!invite.isPending()) {
            throw ConflictException.inviteNotPending();
        }
        if (invite.isExpired(LocalDateTime.now())) {
            throw ConflictException.inviteExpired();
        }
        requireOrgSubscribable(invite.getOrgId());
        assertSeatsAvailable(invite.getOrgId());

        Employee employee = invite.getEmployeeId() == null ? null
                : employees.findByIdForUpdate(invite.getEmployeeId())
                        .filter(e -> invite.getOrgId().equals(e.getOrgId()))
                        .orElseThrow(() -> new NotFoundException("employee", invite.getEmployeeId()));
        if (employee == null || employee.getUserId() != null) {
            // The seat was taken/sold between send and accept (employee deleted, or
            // re-invited and accepted twice).
            throw ConflictException.employeeAlreadyLinked();
        }

        user.acceptInvite(invite.getOrgId(), invite.getRole());
        employee.linkUser(user.getId());
        invite.accept(LocalDateTime.now());
        log.info("Invite accepted: org={} employee={} user={} role={} invite={}",
                invite.getOrgId(), employee.getId(), me.userId(), invite.getRole(), inviteId);

        return AcceptInviteResponse.of(jwtService.issueToken(user), jwtService.ttlSeconds());
    }

    /** Deny: the invite goes DECLINED and the employee seat stays open (re-invitable). */
    @Transactional
    public void decline(UUID inviteId) {
        OrgInvite invite = invites.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("invite", inviteId));
        User user = users.findById(AuthContext.require().userId())
                .orElseThrow(() -> new NotFoundException("user", AuthContext.require().userId()));
        if (user.getEmail() == null || !normalize(user.getEmail()).equals(invite.getEmail())) {
            throw ConflictException.inviteEmailMismatch();
        }
        if (!invite.isPending()) {
            throw ConflictException.inviteNotPending();
        }
        invite.decline();
        log.info("Invite declined: org={} email={} invite={}", invite.getOrgId(), invite.getEmail(), inviteId);
    }

    /** Invites need a live plan or trial. */
    private void requireOrgSubscribable(UUID orgId) {
        if (planLimits.currentPlan(orgId) == null) {
            throw ConflictException.orgNoLiveSubscription();
        }
    }

    /** Seat check against the live plan's max_users — seats are employee rows (the owner is not one). */
    private void assertSeatsAvailable(UUID orgId) {
        planLimits.assertCanAddUser(orgId);
    }

    private Employee employeeOf(OrgInvite invite) {
        if (invite.getEmployeeId() == null) {
            return null;
        }
        return employees.findByIdAndOrgId(invite.getEmployeeId(), invite.getOrgId()).orElse(null);
    }

    private String orgName(UUID orgId) {
        return organizations.findById(orgId).map(org -> org.getName()).orElse(null);
    }

    private String inviterName(OrgInvite invite) {
        return users.findById(invite.getInvitedBy()).map(User::getName).orElse(null);
    }

    /**
     * Emails are normalized lowercase: invites (and all invite comparisons) key on
     * the lowercase form; Firebase emails are lowercase in practice.
     */
    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
