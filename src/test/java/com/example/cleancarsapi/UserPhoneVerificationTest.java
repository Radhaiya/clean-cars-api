package com.example.cleancarsapi;

import com.example.cleancarsapi.dto.SubscribeRequest;
import com.example.cleancarsapi.dto.UserPhoneCheckRequest;
import com.example.cleancarsapi.dto.UserPhoneStartRequest;
import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import com.example.cleancarsapi.service.SubscriptionService;
import com.example.cleancarsapi.service.TwilioGateway;
import com.example.cleancarsapi.service.UserService;
import com.example.cleancarsapi.service.UserPhoneVerificationService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Twilio Verify phone verification of the logged-in ACCOUNT (the plan buyer),
 * against the real schema (Docker MySQL) with the Twilio/Razorpay clouds mocked.
 * Covers: start passes phone+channel through; wrong code -> 400 and nothing
 * persisted; approved code persists users.phone + the flag; the subscribe gate
 * throws 409 phone_verification_required for an unverified owner and lets a
 * verified one reach Razorpay. No seed data — the test builds its own
 * org/plan/user each run.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserPhoneVerificationTest {

    @Autowired UserPhoneVerificationService verificationService;
    @Autowired UserService userService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TwilioGateway twilio;
    @MockitoBean com.example.cleancarsapi.service.RazorpayGateway razorpay;

    Organization org;
    User owner;
    UUID planId;
    String rzpPlanId = "plan_test_" + UUID.randomUUID();

    @BeforeEach
    void setup() {
        // Defensive pre-clean — a previous failed run may have left rows behind
        // (users.email + subscription_plans.razorpay_monthly_plan_id are UNIQUE).
        jdbc.update("DELETE FROM subscription_plans WHERE name = 'PhoneVerifyTestPlan'");
        jdbc.update("UPDATE users SET org_id = NULL WHERE org_id IN "
                + "(SELECT id FROM (SELECT id FROM organizations WHERE name = ?) x)",
                "PhoneVerifyTestOrg");
        jdbc.update("DELETE FROM subscriptions WHERE org_id IN (SELECT id FROM (SELECT id FROM organizations WHERE name = ?) x)",
                "PhoneVerifyTestOrg");
        jdbc.update("DELETE FROM organizations WHERE name = ?", "PhoneVerifyTestOrg");
        jdbc.update("DELETE FROM users WHERE firebase_uid LIKE 'fb-phonetest-%'");

        org = new Organization();
        org.setName("PhoneVerifyTestOrg");
        org.setTimezone("Asia/Kolkata");
        org.setCurrencyCode("USD");
        org.setCurrencySymbol("$");
        org = organizations.save(org);

        planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscription_plans (id, name, razorpay_monthly_plan_id, is_trial, is_public, sort_order)
                VALUES (?, 'PhoneVerifyTestPlan', ?, FALSE, TRUE, 999)
                """, toBytes(planId), rzpPlanId);

        owner = users.save(User.provisionFromFirebase(
                "fb-phonetest-owner-" + UUID.randomUUID(),
                "owner-phonetest-" + UUID.randomUUID() + "@example.com", null, "Owner"));
        User savedOwner = users.findById(owner.getId()).orElseThrow();
        savedOwner.acceptInvite(org.getId(), UserRole.OWNER);
        owner = users.save(savedOwner);

        Subscription sub = new Subscription();
        sub.setOrgId(org.getId());
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setStartDate(java.time.LocalDate.now());
        sub.setEndDate(java.time.LocalDate.now().plusDays(5));
        subscriptions.save(sub);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("UPDATE users SET org_id = NULL WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM subscriptions WHERE org_id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM organizations WHERE id = ?", toBytes(org.getId()));
        jdbc.update("DELETE FROM users WHERE id = ?", toBytes(owner.getId()));
    }

    @Test
    void startPassesPhoneAndChannelToTwilio() {
        verificationService.start(new UserPhoneStartRequest("+919876543210", "whatsapp"));
        verify(twilio).startVerification("+919876543210", "whatsapp");
    }

    @Test
    void wrongCodeThrows400AndPersistsNothing() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        when(twilio.checkVerification("+919876543210", "111111"))
                .thenReturn(TwilioGateway.CheckOutcome.PENDING);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> verificationService.check(
                new UserPhoneCheckRequest("+919876543210", "111111")));
        assertTrue(ex.getMessage().contains("Incorrect or expired"));

        User fresh = users.findById(owner.getId()).orElseThrow();
        assertFalse(fresh.isPhoneVerified());
        assertNull(fresh.getPhoneVerifiedAt());
    }

    @Test
    void expiredOrNeverStartedIsAlso400() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        when(twilio.checkVerification("+919876543210", "999999"))
                .thenReturn(TwilioGateway.CheckOutcome.NO_PENDING_VERIFICATION);
        assertThrows(BadRequestException.class, () -> verificationService.check(
                new UserPhoneCheckRequest("+919876543210", "999999")));
    }

    @Test
    void approvedCodePersistsPhoneAndFlag() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        when(twilio.checkVerification("+919876543210", "123456"))
                .thenReturn(TwilioGateway.CheckOutcome.APPROVED);
        verificationService.check(new UserPhoneCheckRequest("+919876543210", "123456"));

        User fresh = users.findById(owner.getId()).orElseThrow();
        assertTrue(fresh.isPhoneVerified());
        assertNotNull(fresh.getPhoneVerifiedAt());
        assertEquals("+919876543210", fresh.getPhone());
    }

    @Test
    void verifiedPhoneIsVisibleOnMe() {
        owner.applyVerifiedPhone("+919876543210");
        owner = users.save(owner);
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        UserProfile me = userService.getProfile(owner.getId());
        assertTrue(me.phoneVerified());
    }

    @Test
    void subscribeWithoutVerifiedPhoneIs409() {
        authAs(owner.getId(), org.getId(), UserRole.OWNER);
        long subscriptionsBefore = userCount();

        ConflictException ex = assertThrows(ConflictException.class, () -> subscriptionService.subscribe(
                new SubscribeRequest(rzpPlanId)));
        assertEquals("phone_verification_required", ex.getCode());

        // The gate fires before anything else — no Razorpay state was touched.
        verify(razorpay, never()).createSubscription(anyString(), any(), anyString());
        assertEquals(subscriptionsBefore, userCount()); // no PENDING row leaked
    }

    @Test
    void verifiedOwnerReachesRazorpay() {
        owner.applyVerifiedPhone("+919876543210");
        owner = users.save(owner);
        authAs(owner.getId(), org.getId(), UserRole.OWNER);

        com.example.cleancarsapi.service.RazorpayGateway.RazorpaySubscriptionCreated created =
                new com.example.cleancarsapi.service.RazorpayGateway.RazorpaySubscriptionCreated(
                        "sub_razorpay_test", "created", rzpPlanId);
        when(razorpay.createSubscription(anyString(), any(), anyString())).thenReturn(created);

        subscriptionService.subscribe(new SubscribeRequest(rzpPlanId));

        verify(razorpay).createSubscription(anyString(), any(), anyString());
        // The failed Razorpay branch would have flipped the row CANCELLED; success path leaves PENDING.
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE org_id = ? AND status = 'pending'",
                Long.class, toBytes(org.getId())));
    }

    // -------- helpers --------

    private long userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE org_id = ?", Long.class,
                toBytes(org.getId()));
    }

    private long orgUserCount() {
        return users.countByOrgId(org.getId());
    }

    private void authAs(UUID userId, UUID orgId, UserRole role) {
        SecurityContextHolder.clearContext();
        AuthenticatedUser principal = new AuthenticatedUser(userId, orgId, role,
                owner.getEmail(), "Owner");
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
