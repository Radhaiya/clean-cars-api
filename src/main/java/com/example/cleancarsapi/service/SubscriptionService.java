package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ChangePlanRequest;
import com.example.cleancarsapi.dto.ChangePlanResponse;
import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.StartTrialRequest;
import com.example.cleancarsapi.dto.StartTrialResponse;
import com.example.cleancarsapi.dto.SubscribeRequest;
import com.example.cleancarsapi.dto.SubscribeResponse;
import com.example.cleancarsapi.dto.SubscriptionResponse;
import com.example.cleancarsapi.entity.BillingCycle;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * Subscription lifecycle actions (not the CRUD-four split). Two flows:
 * start a free trial — which, for an org-less user, also creates their
 * organization (1 user : 1 org) — and subscribe to a paid plan, which creates
 * the Razorpay subscription and leaves the local row {@code PENDING} until
 * Razorpay's activation webhook lands ({@code RazorpayWebhookService}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    /** The one-time trial's fixed length. Not stored per-plan — there's exactly one Trial plan. */
    public static final int TRIAL_DAYS = 14;

    /** A subscription in one of these states counts as the org's current/live one. */
    private static final Set<SubscriptionStatus> LIVE = EnumSet.of(
            SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    /**
     * States that block subscribing again (PENDING included: a checkout is already in flight;
     * SUSPENDED excluded — they must renew/reactivate, reaching it means paid access is gone).
     */
    private static final Set<SubscriptionStatus> SUBSCRIBE_BLOCKERS = EnumSet.of(
            SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final JwtService jwtService;
    private final RazorpayGateway razorpay;

    /** The caller's current plan — trial or paid. Always a body; org-less caller → {@code NONE}. */
    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrent() {
        AuthenticatedUser me = AuthContext.require();
        if (!me.hasOrg()) {
            return CurrentSubscriptionResponse.none();
        }
        return getCurrentForOrg(me.orgId());
    }

    /** Same lookup as {@link #getCurrent()}, for a known org rather than the caller's own (see {@code UserService}). */
    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrentForOrg(long orgId) {
        return subscriptions.findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, LIVE)
                .map(this::toCurrentResponse)
                // No live row: fall back to the most recent subscription of any status so the
                // frontend sees the real terminal state (CANCELLED/EXPIRED) instead of NONE.
                .or(() -> subscriptions.findFirstByOrgIdOrderByCreatedAtDesc(orgId)
                        .map(this::toCurrentResponse))
                .orElseGet(CurrentSubscriptionResponse::none);
    }

    private CurrentSubscriptionResponse toCurrentResponse(Subscription sub) {
        SubscriptionPlan plan = plans.findById(sub.getPlanId())
                .orElseThrow(() -> new NotFoundException("plan", sub.getPlanId()));
        return CurrentSubscriptionResponse.of(sub, plan, TRIAL_DAYS);
    }

    /**
     * Start a free trial. The caller must be an org-less user: this creates their
     * organization, links them as its owner, opens a {@code trialing} subscription
     * against the one dedicated Trial plan, and returns a fresh access token carrying
     * the new {@code org_id}.
     */
    @Transactional
    public StartTrialResponse startTrial(StartTrialRequest request) {
        AuthenticatedUser me = AuthContext.require();

        // Row-lock the user so two concurrent starts serialise on the org / trial_used latches.
        User user = users.findByIdForUpdate(me.userId())
                .orElseThrow(() -> new NotFoundException("user", me.userId()));
        if (user.getOrgId() != null) {
            throw ConflictException.userAlreadyHasOrg();
        }
        if (user.isTrialUsed()) {
            throw ConflictException.trialAlreadyUsed();
        }

        SubscriptionPlan plan = plans.findByIsTrialTrue()
                .orElseThrow(() -> new IllegalStateException("No Trial plan configured"));

        ZoneId timezone = parseTimezone(request.timezone());

        Organization org = new Organization();
        org.setName(request.orgName().trim());
        org.setTimezone(timezone.getId());
        org.setContactPhone(trimToNull(request.contactPhone()));
        org.setContactEmail(trimToNull(request.contactEmail()));
        org.setAddress(trimToNull(request.address()));
        org = organizations.save(org);

        user.assignToOrgAsOwner(org.getId());
        user.markTrialUsed();
        users.save(user);

        LocalDate today = LocalDate.now();
        Subscription subscription = new Subscription();
        subscription.setOrgId(org.getId());
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setStartDate(today);
        subscription.setEndDate(today.plusDays(TRIAL_DAYS));
        Subscription saved = subscriptions.save(subscription);

        log.info("Trial started: newOrg={} user={} plan={} endDate={}",
                org.getId(), me.userId(), plan.getName(), saved.getEndDate());

        return StartTrialResponse.of(
                SubscriptionResponse.of(saved, plan, TRIAL_DAYS),
                jwtService.issueToken(user),
                jwtService.ttlSeconds());
    }

    /**
     * Start a paid subscription: creates the Razorpay subscription (Razorpay owns
     * price/cycle/charging) and a local {@code PENDING} row. Converting from a live
     * trial is allowed — the new row supersedes the trial when Razorpay activates
     * it (trial days don't pause the calendar; the trial was free). Subscribing
     * while already paid/pending is a 409.
     *
     * <p>The caller hands us a Razorpay Plan ID (the id they picked on the pricing
     * page); the Razorpay plan implies both the featured plan and the billing cycle,
     * so we resolve the internal row by whichever razorpay plan-id column holds it.
     *
     * @return the local row's id plus the Razorpay ids the frontend needs for Checkout
     */
    @Transactional
    public SubscribeResponse subscribe(SubscribeRequest request) {
        AuthenticatedUser me = AuthContext.require();
        long orgId = me.requireOrgId();

        String razorpayPlanId = request.razorpayPlanId().trim();
        SubscriptionPlan plan = resolvePlanByRazorpayId(razorpayPlanId);
        if (!plan.isPublic()) {
            throw new NotFoundException("razorpay_plan_id", razorpayPlanId);
        }
        // The Razorpay plan id already encodes the cycle: which column it matched decides monthly/yearly.
        BillingCycle cycle = razorpayPlanId.equals(plan.getRazorpayMonthlyPlanId())
                ? BillingCycle.MONTHLY : BillingCycle.YEARLY;
        if (subscriptions.existsByOrgIdAndStatusIn(orgId, SUBSCRIBE_BLOCKERS)) {
            throw ConflictException.orgAlreadySubscribed();
        }

        var created = razorpay.createSubscription(razorpayPlanId, orgId, me.userId());

        Subscription subscription = new Subscription();
        subscription.setOrgId(orgId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setStartDate(LocalDate.now());
        subscription.setRazorpaySubscriptionId(created.id());
        subscription.setBillingCycle(cycle);
        Subscription saved = subscriptions.save(subscription);

        log.info("Subscribe started: org={} user={} plan={} cycle={} razorpay={}",
                orgId, me.userId(), plan.getName(), cycle, created.id());

        return SubscribeResponse.of(saved, plan, razorpayPlanId, razorpay.keyId());
    }

    /**
     * Change the plan on the org's live (ACTIVE) Razorpay subscription. Razorpay does
     * the proration — upgrade charges only the remaining amount, downgrade refunds the
     * difference — on the existing autopay; no new checkout. The local row's plan and
     * cycle flip when Razorpay's {@code subscription.updated} webhook lands (see
     * {@code RazorpayWebhookService}), so this endpoint just validates and delegates.
     */
    @Transactional
    public ChangePlanResponse changePlan(ChangePlanRequest request) {
        AuthenticatedUser me = AuthContext.require();
        long orgId = me.requireOrgId();

        Subscription subscription = subscriptions
                .findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, EnumSet.of(SubscriptionStatus.ACTIVE))
                .orElseThrow(() -> new ConflictException("plan_change_requires_active_subscription",
                        "Plan changes need an ACTIVE subscription (start or wait for payment first)"));
        if (subscription.getRazorpaySubscriptionId() == null) {
            throw new ConflictException("plan_change_requires_active_subscription",
                    "Plan changes need a Razorpay-managed subscription");
        }

        String newRazorpayPlanId = request.razorpayPlanId().trim();
        SubscriptionPlan newPlan = resolvePlanByRazorpayId(newRazorpayPlanId);
        String newBillingCycle = newRazorpayPlanId.equals(newPlan.getRazorpayMonthlyPlanId())
                ? BillingCycle.MONTHLY.name() : BillingCycle.YEARLY.name();
        if (newPlan.getId().equals(subscription.getPlanId())) {
            throw new ConflictException("plan_change_same_plan",
                    newPlan.getName() + " (" + newBillingCycle
                    + ") is already the org's current plan+cycle — nothing to change");
        }

        razorpay.updateSubscription(subscription.getRazorpaySubscriptionId(), newRazorpayPlanId);

        String oldPlanName = plans.findById(subscription.getPlanId())
                .map(SubscriptionPlan::getName).orElse("plan#" + subscription.getPlanId());
        log.info("Plan change requested: org={} razorpaySub={} {} -> {} ({})",
                orgId, subscription.getRazorpaySubscriptionId(), oldPlanName,
                newPlan.getName(), newRazorpayPlanId);

        return ChangePlanResponse.of(
                subscription,
                plans.findById(subscription.getPlanId())
                        .orElseThrow(() -> new NotFoundException("plan", subscription.getPlanId())),
                newPlan, newBillingCycle, newRazorpayPlanId, razorpay.keyId());
    }

    /** Plan row whose razorpay monthly OR yearly plan id matches (shared by subscribe / change-plan). */
    private SubscriptionPlan resolvePlanByRazorpayId(String razorpayPlanId) {
        return plans.findByRazorpayMonthlyPlanIdOrRazorpayYearlyPlanId(razorpayPlanId, razorpayPlanId)
                .orElseThrow(() -> new NotFoundException("razorpay_plan_id", razorpayPlanId));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Bean validation can't check IANA validity — reject anything {@link ZoneId} can't resolve. */
    private static ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone: " + timezone.trim());
        }
    }
}
