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
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.exception.RazorpayApiException;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
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
     * HALTED excluded — they must renew/reactivate, reaching it means paid access is gone).
     */
    private static final Set<SubscriptionStatus> SUBSCRIBE_BLOCKERS = EnumSet.of(
            SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final JwtService jwtService;
    private final RazorpayGateway razorpay;
    private final SubscriptionSyncService syncService;
    /** Programmatic transactions: {@link #subscribe} interleaves a committed DB write
     * with a live Razorpay HTTP call, which one big {@code @Transactional} can't do. */
    private final TransactionTemplate tx;

    /** The caller's current plan — trial or paid. Always a body; org-less caller → {@code NONE}. */
    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrent() {
        AuthenticatedUser me = AuthContext.require();
        if (!me.hasOrg()) {
            return CurrentSubscriptionResponse.none();
        }
        return getCurrentForOrg(me.orgId());
    }

    /**
     * Checkout polling callback — the UI calls this on a loop after starting a
     * Razorpay Checkout, keyed by the local subscription id returned from
     * {@code POST /api/subscription/subscribe}. Org-scoped (404 for another org's
     * id, so a wrong/guessed id leaks nothing) and stable across the whole
     * lifecycle: starts as {@code PENDING} (active=false), flips to ACTIVE (plus
     * trial supersede) the moment Razorpay's activation webhook lands.
     */
    // No @Transactional(readOnly) — a deliberately *committed* sync must be able to
    // flush while polling (read-only tx would keep the persistence context in
    // flush-manual mode and silently drop the transition).
    public CurrentSubscriptionResponse getStatus(UUID subscriptionId) {
        AuthenticatedUser me = AuthContext.require();
        UUID orgId = me.requireOrgId();
        UUID rowOrgId = orgId;
        Subscription sub = subscriptions.findById(subscriptionId)
                .filter(s -> s.getOrgId().equals(rowOrgId))
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        if (sub.getStatus() == SubscriptionStatus.PENDING && sub.getRazorpaySubscriptionId() != null) {
            // Self-healing poll: while checkout is still pending, ask Razorpay directly.
            // A lost activation webhook (payment succeeded, push never landed) heals here
            // on the next poll, 2-3 s later, before the support runbook is ever needed.
            // Fail-soft: Razorpay being unreachable must not error the UI loop — the next
            // poll retries; sync is idempotent and never fabricates ACTIVE without
            // Razorpay's period dates (created/authenticated stay PENDING).
            boolean synced = syncService
                    .syncFromRazorpay(subscriptionId, SubscriptionSyncService.Mode.FAIL_SOFT) != null;
            if (synced) {
                sub = subscriptions.findById(subscriptionId)
                        .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
            }
        }
        return toCurrentResponse(sub);
    }

    /** Same lookup as {@link #getCurrent()}, for a known org rather than the caller's own (see {@code UserService}). */
    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrentForOrg(UUID orgId) {
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
        Currency currency = ReferenceDataService.requireCurrency(request.currency());

        Organization org = new Organization();
        org.setName(request.orgName().trim());
        org.setTimezone(timezone.getId());
        org.setCurrencyCode(currency.getCurrencyCode());
        org.setCurrencySymbol(ReferenceDataService.symbolOf(currency));
        org.setContactPhone(trimToNull(request.contactPhone()));
        org.setContactEmail(trimToNull(request.contactEmail()));
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
     * <p>Ordering matters for money safety: the local {@code PENDING} row is committed
     * BEFORE Razorpay is called, so a rollback can never orphan a live Razorpay
     * subscription (a webhook would then retry forever against a missing row).
     * If the Razorpay call fails, the row is flipped {@code CANCELLED} in its own
     * transaction — it escapes the {@link #SUBSCRIBE_BLOCKERS} set and the org can
     * retry immediately. {@code expire_by} is set on the Razorpay subscription, so
     * an abandoned checkout self-cancels on Razorpay's side and the same webhook
     * clears the local row.
     *
     * @return the local row's id plus the Razorpay ids the frontend needs for Checkout
     */
    public SubscribeResponse subscribe(SubscribeRequest request) {
        AuthenticatedUser me = AuthContext.require(UserRole.OWNER);
        UUID orgId = me.requireOrgId();

        String razorpayPlanId = request.razorpayPlanId().trim();

        TxPendingSubscription ctx = tx.execute(status -> {
            // Lock the org row so two concurrent subscribes serialise (the blocker
            // exists-check would otherwise race) — same pattern as startTrial's user lock.
            organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("org", orgId));
            return createPendingSubscription(orgId, razorpayPlanId, me);
        });

        try {
            var created = razorpay.createSubscription(razorpayPlanId, orgId, me.email());
            tx.executeWithoutResult(status -> linkRazorpaySubscription(ctx.localSubscriptionId(), created.id()));
            log.info("Subscribe started: org={} user={} plan={} cycle={} razorpay={}",
                    orgId, me.userId(), ctx.plan().getName(),
                    razorpayPlanId.equals(ctx.plan().getRazorpayMonthlyPlanId())
                            ? BillingCycle.MONTHLY : BillingCycle.YEARLY,
                    created.id());
            return SubscribeResponse.of(loadSubscription(ctx.localSubscriptionId()), ctx.plan(),
                    razorpayPlanId, razorpay.keyId());
        } catch (RuntimeException e) {
            // Razorpay rejected / failed / timed out after the local row committed —
            // release the PENDING blocker so the org can retry immediately.
            cancelPendingCheckout(orgId);
            throw e;
        }
    }

    /** Validate + insert the local PENDING row (must run inside a transaction — no Razorpay call here). */
    private TxPendingSubscription createPendingSubscription(UUID orgId, String razorpayPlanId, AuthenticatedUser me) {
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

        Subscription subscription = new Subscription();
        subscription.setOrgId(orgId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setStartDate(LocalDate.now());
        subscription.setBillingCycle(cycle);
        Subscription saved = subscriptions.save(subscription);

        log.info("Subscribe row created pending Razorpay: org={} user={} plan={} cycle={} local={}",
                orgId, me.userId(), plan.getName(), cycle, saved.getId());
        return new TxPendingSubscription(saved.getId(), plan);
    }

    /** Write the Razorpay subscription id onto the already-committed local row (requires an active transaction). */
    private void linkRazorpaySubscription(UUID localSubscriptionId, String razorpaySubscriptionId) {
        Subscription subscription = subscriptions.findById(localSubscriptionId)
                .orElseThrow(() -> new IllegalStateException("Pending subscription row vanished: " + localSubscriptionId));
        subscription.setRazorpaySubscriptionId(razorpaySubscriptionId);
        subscriptions.save(subscription);
    }

    /** {@link #cancelPendingCheckout(UUID)} for the authenticated caller's own org. */
    public void cancelPendingCheckoutForCaller() {
        AuthenticatedUser me = AuthContext.require(UserRole.OWNER);
        cancelPendingCheckout(me.requireOrgId());
    }

    /**
     * Release a stranded {@code PENDING} reservation (an abandoned checkout, or the
     * fallback when the Razorpay call itself failed). Reconcile-first: fetch
     * Razorpay's own state before any destructive action.
     *
     * <ul>
     *   <li>Razorpay agrees it is still an unsettled checkout ({@code created/pending})
     *       → DELETE it there and flip the row {@code CANCELLED} (unchanged behaviour).</li>
     *   <li>Razorpay says the payment landed ({@code active/charged}) — a lost
     *       webhook — → the sync path activates instead; nothing is cancelled, the
     *       money the customer paid turns into access.</li>
     *   <li>Razorpay unreachable → cancel locally only, never DELETE unseen.</li>
     * </ul>
     */
    public void cancelPendingCheckout(UUID orgId) {
        tx.executeWithoutResult(status -> {
            Subscription subscription = subscriptions
                    .findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, EnumSet.of(SubscriptionStatus.PENDING))
                    .orElseThrow(() -> new ConflictException("no_pending_checkout",
                            "There is no pending checkout to cancel"));
            if (subscription.getRazorpaySubscriptionId() != null) {
                RazorpayGateway.RazorpaySubscription rzp;
                try {
                    rzp = razorpay.fetchSubscription(subscription.getRazorpaySubscriptionId());
                } catch (RazorpayApiException e) {
                    log.warn("Razorpay unreachable for pending checkout {} — cancelling locally only, "
                            + "no DELETE sent (org {})", subscription.getRazorpaySubscriptionId(), orgId, e);
                    subscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptions.save(subscription);
                    return;
                }
                if (rzp.status() != null && Set.of("active", "activated", "resumed").contains(rzp.status())) {
                    // Money moved (webhook was lost) — heal, never cancel.
                    log.info("cancel-checkout on org {} turned into activation sync: Razorpay says {}",
                            orgId, rzp.status());
                    syncService.applyActivation(subscription, rzp.planId(),
                            rzp.currentStart(), rzp.currentEnd(), rzp.paymentMethod());
                    subscriptions.save(subscription);
                    return;
                }
                if (rzp.status() != null
                        && Set.of("pending", "halted", "cancelled", "expired", "completed").contains(rzp.status())) {
                    // Razorpay already moved past created — mirror it instead of deleting.
                    syncService.applyRazorpayState(subscription, rzp);
                    subscriptions.save(subscription);
                    log.info("Pending checkout converged to Razorpay state {}: org={} local={}",
                            rzp.status(), orgId, subscription.getId());
                    return;
                }
                // created/authenticated: a genuine dead checkout — safe to cancel.
                try {
                    razorpay.cancelSubscription(subscription.getRazorpaySubscriptionId());
                } catch (RuntimeException e) {
                    log.warn("Razorpay cancel failed for pending checkout {} — still cancelling locally",
                            subscription.getRazorpaySubscriptionId(), e);
                }
            }
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscriptions.save(subscription);
            log.info("Pending checkout cancelled locally: org={} local={}", orgId, subscription.getId());
        });
    }

    private record TxPendingSubscription(UUID localSubscriptionId, SubscriptionPlan plan) {}

    private Subscription loadSubscription(UUID id) {
        return subscriptions.findById(id)
                .orElseThrow(() -> new IllegalStateException("Subscription vanished: " + id));
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
        AuthenticatedUser me = AuthContext.require(UserRole.OWNER);
        UUID orgId = me.requireOrgId();

        // Serialize against subscribe / another plan change before any Razorpay call.
        organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("org", orgId));

        Subscription subscription = subscriptions
                .findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, EnumSet.of(SubscriptionStatus.ACTIVE))
                .orElseThrow(() -> new ConflictException("plan_change_requires_active_subscription",
                        "Plan changes need an ACTIVE subscription (start or wait for payment first)"));
        if (subscription.getRazorpaySubscriptionId() == null) {
            throw new ConflictException("plan_change_requires_active_subscription",
                    "Plan changes need a Razorpay-managed subscription");
        }
        if ("upi".equalsIgnoreCase(subscription.getPaymentMethod())) {
            // Razorpay PATCHes UPI-mandate subscriptions with 400 "subscriptions cannot
            // be updated when payment mode is upi" — UPI autopay mandates are fixed.
            // The UI path for these is cancel + fresh checkout, not a plan change.
            throw new ConflictException("plan_change_unsupported_upi",
                    "Plan changes are not supported on UPI autopay subscriptions — "
                    + "cancel and subscribe to the new plan instead");
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
