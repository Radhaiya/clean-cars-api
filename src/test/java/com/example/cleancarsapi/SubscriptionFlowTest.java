package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.SubscribeRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.exception.RazorpayApiException;
import com.example.cleancarsapi.service.SubscriptionExpiryJob;
import com.example.cleancarsapi.service.SubscriptionSyncService;
import com.example.cleancarsapi.service.RazorpayGateway;
import com.example.cleancarsapi.service.RazorpayGateway.RazorpaySubscriptionCreated;
import com.example.cleancarsapi.service.SubscriptionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The subscription money-flow against the real schema (Docker MySQL): the
 * PENDING-before-Razorpay ordering, the Razorpay-failure fallback, plan blockers,
 * stuck-checkout release, the polling endpoint and the trial-expiry job.
 * No seed data — the test builds its own plan/org/users each run.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionFlowTest {

    @Autowired SubscriptionService subscriptionService;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    /** No real Razorpay calls in tests — stubbed per test. */
    @MockitoBean RazorpayGateway razorpayGateway;
    @Autowired SubscriptionSyncService syncService;

    Organization org;
    UUID trialPlan;
    UUID paidPlan;
    String paidMonthlyRzpPlan = "plan_monthly_" + UUID.randomUUID();

    @BeforeEach
    void setup() {
        trialPlan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation, is_public, sort_order)
                VALUES (?, 'SubFlowTrial', TRUE, 5, FALSE, FALSE, 998)
                """, toBytes(trialPlan));
        paidPlan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscription_plans (id, name, is_trial, max_users, invoice_generation,
                                                is_public, sort_order, razorpay_monthly_plan_id)
                VALUES (?, 'SubFlowPaid', FALSE, 5, FALSE, TRUE, 999, ?)
                """, toBytes(paidPlan), paidMonthlyRzpPlan);

        org = new Organization();
        org.setName("SubFlowOrg");
        org.setTimezone("Asia/Kolkata");
        org.setCurrencyCode("USD");
        org.setCurrencySymbol("$");
        org = organizations.save(org);

        User unsaved = users.save(User.provisionFromFirebase("fb-subflow-owner",
                "owner-subflow@example.com", null, "Owner"));
        User savedOwner = users.findById(unsaved.getId()).orElseThrow();
        savedOwner.acceptInvite(org.getId(), UserRole.OWNER);
        owner = users.save(savedOwner);
    }

    User owner;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM users WHERE email = 'owner-subflow@example.com'");
        jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM subscription_plans WHERE id IN (?, ?)",
                toBytes(trialPlan), toBytes(paidPlan));
    }

    // -------- subscribe: local PENDING first, Razorpay second --------

    @Test
    void razorpayFailureLeavesRowCancelledAndBlockerReleased() {
        authAsOwner();
        when(razorpayGateway.createSubscription(anyString(), any(UUID.class), anyString()))
                .thenThrow(new RazorpayApiException("razorpay down"));

        assertThrows(RazorpayApiException.class,
                () -> subscriptionService.subscribe(new SubscribeRequest(paidMonthlyRzpPlan)));

        // The fallback flipped the PENDING row CANCELLED — no live blocker left.
        Subscription row = subscriptions.findFirstByOrgIdOrderByCreatedAtDesc(org.getId()).orElseThrow();
        assertEquals(SubscriptionStatus.CANCELLED, row.getStatus());
        assertNull(row.getRazorpaySubscriptionId());

        // Retry: Razorpay succeeds this time and the blocker does not fire.
        when(razorpayGateway.createSubscription(anyString(), any(UUID.class), anyString()))
                .thenReturn(new RazorpaySubscriptionCreated("sub_ok_1", "created", paidMonthlyRzpPlan));
        var response = subscriptionService.subscribe(new SubscribeRequest(paidMonthlyRzpPlan));
        assertEquals("sub_ok_1", response.razorpaySubscriptionId());
        assertEquals(SubscriptionStatus.PENDING,
                subscriptions.findById(response.subscriptionId()).orElseThrow().getStatus());
    }

    @Test
    void subscribeHappyPathThenBlockedWhilePending() {
        authAsOwner();
        when(razorpayGateway.createSubscription(anyString(), any(UUID.class), anyString()))
                .thenReturn(new RazorpaySubscriptionCreated("sub_ok_2", "created", paidMonthlyRzpPlan));

        var response = subscriptionService.subscribe(new SubscribeRequest(paidMonthlyRzpPlan));
        assertEquals("sub_ok_2", response.razorpaySubscriptionId());
        Subscription row = subscriptions.findById(response.subscriptionId()).orElseThrow();
        assertEquals(SubscriptionStatus.PENDING, row.getStatus());
        assertEquals("sub_ok_2", row.getRazorpaySubscriptionId());
        assertEquals("MONTHLY", row.getBillingCycle().name());

        // A second subscribe while PENDING (checkout in flight) is a 409 blocker.
        assertThrows(ConflictException.class,
                () -> subscriptionService.subscribe(new SubscribeRequest(paidMonthlyRzpPlan)));
    }

    @Test
    void blockedWhenLiveSubscriptionExists() {
        authAsOwner();
        insertSubscription(org.getId(), trialPlan, SubscriptionStatus.ACTIVE);
        assertThrows(ConflictException.class,
                () -> subscriptionService.subscribe(new SubscribeRequest(paidMonthlyRzpPlan)));
    }

    // -------- cancel-checkout (stuck PENDING release) --------

    @Test
    void cancelCheckoutReleasesPendingRow() {
        authAsOwner();
        UUID pendingId = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.PENDING);
        jdbc.update("UPDATE subscriptions SET razorpay_subscription_id = 'abandoned_rzp_1' WHERE id = ?",
                toBytes(pendingId));
        stubRazorpay("abandoned_rzp_1", "created", null, null, null);

        subscriptionService.cancelPendingCheckoutForCaller();

        assertEquals(SubscriptionStatus.CANCELLED, subscriptions.findById(pendingId).orElseThrow().getStatus());
        verify(razorpayGateway).cancelSubscription("abandoned_rzp_1");
        // No pending row left — a second cancel is a coded 409.
        ConflictException conflict = assertThrows(ConflictException.class,
                () -> subscriptionService.cancelPendingCheckoutForCaller());
        assertEquals("no_pending_checkout", conflict.getCode());
    }

    // -------- self-healing poll (lost activation webhook) --------

    @Test
    void pollingSelfHealsWhenActivationWebhookWasLost() {
        authAsOwner();
        UUID trialId = insertSubscription(org.getId(), trialPlan, SubscriptionStatus.TRIALING);
        UUID pendingId = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.PENDING);
        jdbc.update("UPDATE subscriptions SET razorpay_subscription_id = 'rzp_lost_1' WHERE id = ?",
                toBytes(pendingId));
        long now = System.currentTimeMillis() / 1000;
        stubRazorpay("rzp_lost_1", "active", paidMonthlyRzpPlan, now - 86400, now + 86400);

        var view = subscriptionService.getStatus(pendingId);

        // The poll fetched Razorpay, applied the activation, and reports the live row.
        assertEquals(SubscriptionStatus.ACTIVE.name(), view.status());
        assertTrue(view.active());
        // And the still-live trial was superseded, exactly like the webhook would.
        assertEquals(SubscriptionStatus.CANCELLED, subscriptions.findById(trialId).orElseThrow().getStatus());
        // Gateway's DELETE was never on the table — money became access.
        verify(razorpayGateway, never()).cancelSubscription(anyString());
    }

    @Test
    void pollingStaysPendingWhenRazorpayDownOrNotSettled() {
        authAsOwner();
        UUID pendingId = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.PENDING);
        jdbc.update("UPDATE subscriptions SET razorpay_subscription_id = 'rzp_p_9' WHERE id = ?",
                toBytes(pendingId));

        // Razorpay unreachable → fail-soft: 200, local snapshot unchanged.
        doThrow(new RazorpayApiException("connection refused"))
                .when(razorpayGateway).fetchSubscription("rzp_p_9");
        var view = subscriptionService.getStatus(pendingId);
        assertEquals(SubscriptionStatus.PENDING.name(), view.status());
        assertFalse(view.active());

        // created/authenticated → not money-settled: stays PENDING, never fabricated ACTIVE.
        stubRazorpay("rzp_p_9", "authenticated", paidMonthlyRzpPlan, null, null);
        view = subscriptionService.getStatus(pendingId);
        assertEquals(SubscriptionStatus.PENDING.name(), view.status());
        assertFalse(view.active());
    }

    // -------- admin console sync (support runbook for closed browsers) --------

    @Test
    void consoleSyncReconcilesRowFromRazorpay() {
        UUID pendingId = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.PENDING);
        jdbc.update("UPDATE subscriptions SET razorpay_subscription_id = 'rzp_admin_1' WHERE id = ?",
                toBytes(pendingId));
        long now = System.currentTimeMillis() / 1000;
        stubRazorpay("rzp_admin_1", "active", paidMonthlyRzpPlan, now - 7200, now + 7 * 86400);

        syncService.syncFromRazorpay(pendingId, SubscriptionSyncService.Mode.FAIL_LOUD);

        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findById(pendingId).orElseThrow().getStatus());
        // Idempotent: a late webhook (or a second sync) changes nothing.
        when(razorpayGateway.fetchSubscription("rzp_admin_1"))
                .thenReturn(new RazorpayGateway.RazorpaySubscription("rzp_admin_1", "active",
                        paidMonthlyRzpPlan, now - 7200, now + 7 * 86400, "card"));
        syncService.syncFromRazorpay(pendingId, SubscriptionSyncService.Mode.FAIL_LOUD);
        Subscription reloaded = subscriptions.findById(pendingId).orElseThrow();
        assertEquals(SubscriptionStatus.ACTIVE, reloaded.getStatus());
        assertEquals("MONTHLY", reloaded.getBillingCycle().name());
    }

    // -------- polling endpoint --------

    @Test
    void pollingReturnsOwnRowAnd404sForeignRows() {
        authAsOwner();
        UUID mine = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.ACTIVE);

        var view = subscriptionService.getStatus(mine);
        assertEquals(SubscriptionStatus.ACTIVE.name(), view.status());

        // Another org's subscription id → 404 (no cross-org leak)
        Organization otherOrg = new Organization();
        otherOrg.setName("SubFlowOther-" + UUID.randomUUID());
        otherOrg.setTimezone("Asia/Kolkata");
        otherOrg.setCurrencyCode("USD");
        otherOrg.setCurrencySymbol("$");
        otherOrg = organizations.save(otherOrg);
        try {
            UUID foreign = insertSubscription(otherOrg.getId(), paidPlan, SubscriptionStatus.ACTIVE);
            assertThrows(NotFoundException.class, () -> subscriptionService.getStatus(foreign));
        } finally {
            jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", toBytes(otherOrg.getId()));
            jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(otherOrg.getId()));
        }

        // Terminal rows report their real status through the same endpoint.
        jdbc.update("UPDATE subscriptions SET status = 'EXPIRED' WHERE id = ?", toBytes(mine));
        assertEquals(SubscriptionStatus.EXPIRED.name(), subscriptionService.getStatus(mine).status());
        assertFalse(subscriptionService.getStatus(mine).active());
    }

    // -------- trial expiry job --------

    @Test
    void expiryJobFlipsOnlyLapsedTrials() {
        UUID lapsed = insertSubscription(org.getId(), trialPlan, SubscriptionStatus.TRIALING);
        jdbc.update("UPDATE subscriptions SET end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(1)), toBytes(lapsed));
        UUID futureTrial = insertSubscription(org.getId(), trialPlan, SubscriptionStatus.TRIALING);
        jdbc.update("UPDATE subscriptions SET end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now().plusDays(7)), toBytes(futureTrial));
        UUID active = insertSubscription(org.getId(), paidPlan, SubscriptionStatus.ACTIVE);
        jdbc.update("UPDATE subscriptions SET end_date = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(1)), toBytes(active));

        new SubscriptionExpiryJob(subscriptions).expireLapsedTrials();

        assertEquals(SubscriptionStatus.EXPIRED, subscriptions.findById(lapsed).orElseThrow().getStatus());
        assertEquals(SubscriptionStatus.TRIALING, subscriptions.findById(futureTrial).orElseThrow().getStatus());
        // Paid rows are Razorpay's business — the job never touches them, even when lapsed.
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findById(active).orElseThrow().getStatus());
    }

    // -------- helpers --------

    private void stubRazorpay(String id, String status, String planId, Long start, Long end) {
        doReturn(new RazorpayGateway.RazorpaySubscription(id, status, planId, start, end, "card"))
                .when(razorpayGateway).fetchSubscription(id);
    }

    private void authAsOwner() {
        SecurityContextHolder.clearContext();
        AuthenticatedUser principal = new AuthenticatedUser(owner.getId(), org.getId(),
                UserRole.OWNER, "owner-subflow@example.com", "Owner");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private UUID insertSubscription(UUID orgId, UUID planId, SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setOrgId(orgId);
        sub.setPlanId(planId);
        sub.setStatus(status);
        sub.setStartDate(LocalDate.now().minusDays(1));
        sub.setEndDate(LocalDate.now().plusDays(5));
        return subscriptions.save(sub).getId();
    }

    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
