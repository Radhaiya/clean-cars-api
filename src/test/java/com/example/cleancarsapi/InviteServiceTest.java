package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.AcceptInviteResponse;
import com.example.cleancarsapi.dto.EmployeeRequest;
import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.dto.InviteRequest;
import com.example.cleancarsapi.dto.InviteResponse;
import com.example.cleancarsapi.dto.SubscribeRequest;
import com.example.cleancarsapi.entity.InviteStatus;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.OrgInviteRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.EmployeeCreateService;
import com.example.cleancarsapi.service.EmployeeDeleteService;
import com.example.cleancarsapi.service.InviteService;
import com.example.cleancarsapi.service.PlanService;
import com.example.cleancarsapi.service.SubscriptionService;
import com.example.cleancarsapi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Employee-seat invite flow against the real schema (Docker MySQL): the plan's
 * maxUsers caps the org's {@code employees} roster; an invite is addressed to an
 * employee row and linking happens on accept ({@code employees.user_id}).
 * No seed data — the test builds its own plan/org/users each run.
 * Rules under test come from docs/FEATURE-INVITES.md.
 */
@SpringBootTest
@ActiveProfiles("test-api")
class InviteServiceTest {

    @Autowired InviteService inviteService;
    @Autowired UserService userService;
    @Autowired EmployeeCreateService employeeCreateService;
    @Autowired EmployeeDeleteService employeeDeleteService;
    @Autowired PlanService planService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired JdbcTemplate jdbc;

    Organization org;
    UUID planId;
    User owner;
    User invitee;
    User extraUser; // created inside individual tests that need a third account

    @BeforeEach
    void setup() {
        planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, is_public, sort_order)
                VALUES (?, 'InvTestPlan', FALSE, 5, FALSE, FALSE, 999)
                """, toBytes(planId));

        org = new Organization();
        org.setName("InvTestOrg");
        org.setTimezone("Asia/Kolkata");
        org.setCurrencyCode("USD");
        org.setCurrencySymbol("$");
        org = organizations.save(org);

        Subscription sub = new Subscription();
        sub.setOrgId(org.getId());
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setStartDate(LocalDate.now());
        sub.setEndDate(LocalDate.now().plusDays(5));
        subscriptions.save(sub);

        owner = users.save(User.provisionFromFirebase("fb-invtest-owner",
                "owner-invtest@example.com", null, "Owner"));
        User savedOwner = users.findById(owner.getId()).orElseThrow();
        savedOwner.acceptInvite(org.getId(), UserRole.OWNER);
        owner = users.save(savedOwner);

        invitee = users.save(User.provisionFromFirebase("fb-invtest-invitee",
                "invitee-invtest@example.com", null, "Invitee"));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("DELETE FROM org_invites WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM service_orders WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM employees WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("""
                DELETE FROM users WHERE email IN
                ('owner-invtest@example.com', 'invitee-invtest@example.com', 'other-invtest@example.com')
                """);
        jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM subscription_plans WHERE id = ?", toBytes(planId));
        extraUser = null;
    }

    // -------- sending --------

    @Test
    void ownerSendsPendingInviteToEmployee() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");

        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        InviteResponse response =
                inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));

        assertEquals(InviteStatus.PENDING, response.status());
        assertEquals("invitee-invtest@example.com", response.email()); // normalized lowercase
        assertEquals(UserRole.WORKER, response.role());
        assertEquals(employee.id(), response.employeeId());
        assertEquals("Invitee", response.employeeName());
        assertNotNull(response.expiresAt());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM org_invites WHERE org_id = ?", Integer.class, toBytes(org.getId())));
    }

    @Test
    void nonOwnerCannotSeePlansOrSubscriptionChanges() {
        // Invited members (managers/workers) have no plan decision to make:
        // the catalogue and the money-mutation endpoints are owner-only.
        authAs(owner.getId(), org.getId(), UserRole.MANAGER);
        assertThrows(ForbiddenException.class, () -> planService.listPublic());
        assertThrows(ForbiddenException.class,
                () -> subscriptionService.subscribe(new SubscribeRequest("rzp_test_not_used")));
    }

    @Test
    void nonOwnerCannotSend() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.MANAGER);
        assertThrows(ForbiddenException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER)));
    }

    @Test
    void cannotInviteEmployeeWithoutEmail() {
        EmployeeResponse employee = createEmployee("NoEmail", null);
        authAs(owner.getId(), org.getId(), UserRole.OWNER);

        assertThrows(BadRequestException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER)));
    }

    @Test
    void cannotInviteAlreadyLinkedEmployee() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        // Link the employee's seat manually (as accept would).
        jdbc.update("UPDATE employees SET user_id = ? WHERE id = ?",
                toBytes(invitee.getId()), toBytes(employee.id()));
        authAs(owner.getId(), org.getId(), UserRole.OWNER);

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER)));
        assertEquals("employee_already_linked", conflict.getCode());
    }

    @Test
    void memberOfAnotherOrgCanBeInvitedAndSwitches() {
        // Hard rule 3: any non-owner can switch orgs, any number of times.
        UUID otherOrgId = insertOrg("InvTestOrgB");
        extraUser = users.save(User.provisionFromFirebase(
                "fb-invtest-other", "other-invtest@example.com", null, "Other"));
        User member = users.findById(extraUser.getId()).orElseThrow();
        member.acceptInvite(otherOrgId, UserRole.WORKER);
        users.save(member);

        EmployeeResponse employee = createEmployee("Other", "other-invtest@example.com");
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));

        authAs(extraUser.getId(), otherOrgId, UserRole.WORKER);
        assertEquals(1, inviteService.myInvites().size());
        inviteService.accept(sent.id());

        User after = users.findById(extraUser.getId()).orElseThrow();
        assertEquals(org.getId(), after.getOrgId());

        jdbc.update("DELETE FROM users WHERE id = ?", toBytes(extraUser.getId()));
        jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(otherOrgId));
        extraUser = null;
    }

    @Test
    void cannotInviteMemberOfSameOrg() {
        EmployeeResponse first = createEmployee("First", "invitee-invtest@example.com");
        InviteResponse sent = inviteService.create(new InviteRequest(first.id(), UserRole.WORKER));
        authAs(invitee.getId(), null, UserRole.STAFF);
        inviteService.accept(sent.id());

        // Same account via a second roster row can't take a second seat.
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        User saved = users.findById(invitee.getId()).orElseThrow();
        assertEquals(org.getId(), saved.getOrgId());
        jdbc.update("UPDATE employees SET email = 'x-invtest@example.com' WHERE id = ?", toBytes(first.id()));
        EmployeeResponse dup = createEmployee("Dup", "invitee-invtest@example.com");
        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(dup.id(), UserRole.WORKER)));
        assertEquals("user_already_in_org", conflict.getCode());
    }

    @Test
    void ownerWithLivePaidPlanCannotBeInvited() {
        // Hard rule 1: a live paid plan pins the owner to their org.
        UUID otherOrgId = insertOrg("InvTestOrgPaid");
        insertSubscription(otherOrgId, SubscriptionStatus.ACTIVE);
        makeOwner(invitee, otherOrgId);

        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER)));
        assertEquals("user_has_own_plan", conflict.getCode());

        cleanupOrg(otherOrgId);
    }

    @Test
    void ownerWithExpiredTrialCanAcceptAndLeavesTheirOrg() {
        UUID otherOrgId = insertOrg("InvTestOrgTrial");
        insertSubscription(otherOrgId, SubscriptionStatus.EXPIRED);
        makeOwner(invitee, otherOrgId);

        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));

        authAs(invitee.getId(), otherOrgId, UserRole.OWNER);
        assertEquals(1, inviteService.myInvites().size());
        inviteService.accept(sent.id());

        User after = users.findById(invitee.getId()).orElseThrow();
        assertEquals(org.getId(), after.getOrgId());
        assertEquals(UserRole.WORKER, after.getRole());

        cleanupOrg(otherOrgId);
    }

    @Test
    void liveTrialOwnerCanAccept() {
        UUID otherOrgId = insertOrg("InvTestOrgLiveTrial");
        insertSubscription(otherOrgId, SubscriptionStatus.TRIALING);
        makeOwner(invitee, otherOrgId);

        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        authAs(invitee.getId(), otherOrgId, UserRole.OWNER);
        inviteService.accept(sent.id());

        assertEquals(org.getId(), users.findById(invitee.getId()).orElseThrow().getOrgId());
        cleanupOrg(otherOrgId);
    }

    @Test
    void leaveFreesSeatSoMemberCanBeReinvited() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        authAs(invitee.getId(), null, UserRole.STAFF);
        inviteService.accept(sent.id());
        authAs(invitee.getId(), org.getId(), UserRole.WORKER);
        userService.leaveOrg();

        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        InviteResponse again = inviteService.create(new InviteRequest(employee.id(), UserRole.MANAGER));
        assertEquals(InviteStatus.PENDING, again.status());
    }

    @Test
    void duplicatePendingInviteRejected() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.MANAGER)));
        assertEquals("invite_already_pending", conflict.getCode());
    }

    @Test
    void cannotInviteWithOwnerRole() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.OWNER)));
        assertEquals("invite_invalid_role", conflict.getCode());
    }

    // -------- seats --------

    @Test
    void createEmployeeBlockedAtMaxUsers() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        employeeCreateService.create(org.getId(), new EmployeeRequest("SeatOne", null));
        jdbc.update("UPDATE subscription_plans SET max_users = 1 WHERE id = ?", toBytes(planId));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> employeeCreateService.create(org.getId(),
                        new EmployeeRequest("Extra", null)));
        assertEquals("user_limit_reached", conflict.getCode());
    }

    @Test
    void sendAllowedExactlyAtMaxUsers() {
        // The invited employee row already consumed its own seat at creation, so
        // being exactly at maxUsers must not block inviting it.
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        jdbc.update("UPDATE subscription_plans SET max_users = 1 WHERE id = ?", toBytes(planId));

        InviteResponse invite = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        assertEquals(employee.id(), invite.employeeId());
    }

    @Test
    void sendBlockedWhenRosterExceedsMaxUsers() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        createEmployee("Other", "other-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        jdbc.update("UPDATE subscription_plans SET max_users = 1 WHERE id = ?", toBytes(planId));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER)));
        assertEquals("user_limit_reached", conflict.getCode());
    }

    // -------- accept / decline --------

    @Test
    void acceptLinksUserToEmployeeSeatAndIssuesToken() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        inviteService.create(new InviteRequest(employee.id(), UserRole.MANAGER));

        UUID inviteId = jdbc.queryForObject(
                "SELECT id FROM org_invites WHERE org_id = ? AND status = 'pending' LIMIT 1",
                UUID.class, toBytes(org.getId()));
        authAs(invitee.getId(), null, UserRole.STAFF);

        AcceptInviteResponse accepted = inviteService.accept(inviteId);

        assertNotNull(accepted.token());
        User joined = users.findById(invitee.getId()).orElseThrow();
        assertEquals(org.getId(), joined.getOrgId());
        assertEquals(UserRole.MANAGER, joined.getRole());
        UUID linkedUserId = jdbc.queryForObject(
                "SELECT user_id FROM employees WHERE id = ?", UUID.class, toBytes(employee.id()));
        assertEquals(invitee.getId(), linkedUserId);
        assertEquals(InviteStatus.ACCEPTED, InviteStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM org_invites WHERE id = ?", String.class, toBytes(inviteId)).trim().toUpperCase()));
    }

    @Test
    void acceptAfterExpiryRejected() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        UUID inviteId = jdbc.queryForObject(
                "SELECT id FROM org_invites WHERE org_id = ? LIMIT 1", UUID.class, toBytes(org.getId()));
        jdbc.update("UPDATE org_invites SET expires_at = DATEADD('DAY', -1, NOW()) WHERE id = ?",
                toBytes(inviteId));

        authAs(invitee.getId(), null, UserRole.STAFF);
        ConflictException conflict = assertThrows(ConflictException.class, () -> inviteService.accept(inviteId));
        assertEquals("invite_expired", conflict.getCode());
    }

    @Test
    void acceptWithOtherAccountRejected() {
        extraUser = users.save(User.provisionFromFirebase(
                "fb-invtest-other", "other-invtest@example.com", null, "Other"));
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        UUID inviteId = jdbc.queryForObject(
                "SELECT id FROM org_invites WHERE org_id = ? LIMIT 1", UUID.class, toBytes(org.getId()));

        authAs(extraUser.getId(), null, UserRole.STAFF);
        ConflictException conflict = assertThrows(ConflictException.class, () -> inviteService.accept(inviteId));
        assertEquals("invite_email_mismatch", conflict.getCode());
    }

    @Test
    void declineThenFreshInviteAllowed() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));

        authAs(invitee.getId(), null, UserRole.STAFF);
        inviteService.decline(sent.id());
        assertEquals(InviteStatus.DECLINED, InviteStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM org_invites WHERE id = ?", String.class, toBytes(sent.id()))
                .trim().toUpperCase()));

        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        InviteResponse again = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        assertEquals(InviteStatus.PENDING, again.status());
        assertNotEquals(sent.id(), again.id());
    }

    // -------- employee deletion cascade --------

    @Test
    void deletingEmployeeClearsInviteUnlinkAndJobAssignments() {
        EmployeeResponse employee = createEmployee("Invitee", "invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        authAs(invitee.getId(), null, UserRole.STAFF);
        UUID inviteId = jdbc.queryForObject(
                "SELECT id FROM org_invites WHERE org_id = ? LIMIT 1", UUID.class, toBytes(org.getId()));
        inviteService.accept(inviteId);

        // A job card assigned to this employee, with its own customer (FK NOT NULL).
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customers (id, org_id, name, phone)
                VALUES (?, ?, 'InvTestCustomer', '9999999999')
                """, toBytes(customerId), toBytes(org.getId()));
        jdbc.update("""
                INSERT INTO service_orders (id, org_id, customer_id, employee_id, created_by, status, paid)
                VALUES (?, ?, ?, ?, ?, 'in_progress', FALSE)
                """, toBytes(orderId), toBytes(org.getId()), toBytes(customerId),
                toBytes(employee.id()), toBytes(owner.getId()));

        employeeDeleteService.delete(org.getId(), employee.id());

        Integer assignments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_orders WHERE employee_id = ?", Integer.class, toBytes(employee.id()));
        assertEquals(0, assignments);
        User unlinked = users.findById(invitee.getId()).orElseThrow();
        assertNull(unlinked.getOrgId());
        Integer inviteRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM org_invites WHERE employee_id = ?",
                Integer.class, toBytes(employee.id()));
        assertEquals(0, inviteRows);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE id = ?", Integer.class, toBytes(employee.id())));
        Integer jobsKept = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_orders WHERE id = ?", Integer.class, toBytes(orderId));
        assertEquals(1, jobsKept);

        jdbc.update("DELETE FROM service_orders WHERE id = ?", toBytes(orderId));
        jdbc.update("DELETE FROM customers WHERE id = ?", toBytes(customerId));
    }

    @Test
    void memberLeavesOrg() {
        EmployeeResponse employee = createEmployee("Invitee", "Invitee-invtest@example.com");
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        InviteResponse sent = inviteService.create(new InviteRequest(employee.id(), UserRole.WORKER));
        authAs(invitee.getId(), null, UserRole.STAFF);
        inviteService.accept(sent.id());

        userService.leaveOrg();

        User after = users.findById(invitee.getId()).orElseThrow();
        assertNull(after.getOrgId());
        assertEquals(false, after.isManagedMember());
    }

    @Test
    void ownerCannotLeave() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        ConflictException conflict = assertThrows(ConflictException.class, () -> userService.leaveOrg());
        assertEquals("owner_cannot_leave", conflict.getCode());
    }

    // -------- helpers --------

    private UUID insertOrg(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, name, timezone, currency_code, currency_symbol)
                VALUES (?, ?, 'Asia/Kolkata', 'USD', '$')
                """, toBytes(id), name);
        return id;
    }

    private void insertSubscription(UUID orgId, SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setOrgId(orgId);
        sub.setPlanId(planId);
        sub.setStatus(status);
        sub.setStartDate(LocalDate.now().minusDays(20));
        sub.setEndDate(LocalDate.now().plusDays(status == SubscriptionStatus.EXPIRED ? -5 : 5));
        subscriptions.save(sub);
    }

    private void makeOwner(User user, UUID orgId) {
        User u = users.findById(user.getId()).orElseThrow();
        u.assignToOrgAsOwner(orgId);
        users.save(u);
    }

    private void cleanupOrg(UUID orgId) {
        jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", toBytes(orgId));
        jdbc.update("UPDATE users SET org_id = NULL WHERE org_id = ?", toBytes(orgId));
    }

    private EmployeeResponse createEmployee(String name, String email) {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        return employeeCreateService.create(org.getId(), new EmployeeRequest(name, email));
    }

    private void authAs(UUID userId, UUID orgId, UserRole role) {
        SecurityContextHolder.clearContext();
        AuthenticatedUser principal = new AuthenticatedUser(userId, orgId, role,
                userId.equals(owner.getId()) ? "owner-invtest@example.com"
                        : userId.equals(invitee.getId()) ? "invitee-invtest@example.com"
                        : "other-invtest@example.com", "Test");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    /** BINARY(16) columns take the raw 16-byte UUID form. */
    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
