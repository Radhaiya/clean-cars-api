package com.example.cleancarsapi.service;

import com.example.cleancarsapi.exception.RazorpayApiException;
import com.example.cleancarsapi.entity.BillingCycle;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Pull-based reconciliation with Razorpay — the fallback when a webhook is lost.
 * One method, {@link #syncFromRazorpay}, fetches Razorpay's own state for a local
 * row and applies the <strong>same transition the webhook would</strong>, so the
 * two paths converge instead of diverging:
 *
 * <ul>
 *   <li>the customer polling endpoint (<code>FAIL_SOFT</code>) — checkout loop
 *       self-heals while the row is PENDING;</li>
 *   <li>the admin console sync (<code>FAIL_LOUD</code>) — support's fallback for
 *       checkouts nobody polls (browser closed);</li>
 *   <li>the cancel-checkout guard (<code>FAIL_LOUD</code>) — never DELETE a
 *       Razorpay subscription we cannot confirm is still a dead checkout.</li>
 * </ul>
 *
 * <p>Placement rules mirror the webhook matrix:
 * <code>activated/resumed → ACTIVE</code> (plus trial supersede; the trial was
 * free), <code>pending → PAST_DUE</code>, <code>halted → HALTED</code>,
 * <code>cancelled → CANCELLED</code>, <code>completed/expired → EXPIRED</code>.
 * <code>created</code>/<code>authenticated</code> leave the row PENDING — money is
 * not yet settled, and ACTIVE is never fabricated without Razorpay's period dates.
 * Idempotent: re-running, or running after a late webhook replays, is a no-op.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionSyncService {

    /** Razorpay unreachable/null values: swallow (customer polling must never fail the poll) or throw (admin must see it). */
    public enum Mode { FAIL_SOFT, FAIL_LOUD }

    private final RazorpayGateway razorpay;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;

    /**
     * Reconcile one local row against Razorpay's live state.
     *
     * @return the refreshed row, or null when Razorpay could not be reached
     *         (only possible in {@link Mode#FAIL_SOFT}) or there was nothing to do
     */
    @Transactional
    public Subscription syncFromRazorpay(UUID localSubscriptionId, Mode mode) {
        Subscription sub = subscriptions.findById(localSubscriptionId).orElse(null);
        if (sub == null || sub.getRazorpaySubscriptionId() == null) {
            return null;
        }
        RazorpayGateway.RazorpaySubscription rzp;
        try {
            rzp = razorpay.fetchSubscription(sub.getRazorpaySubscriptionId());
        } catch (RazorpayApiException e) {
            if (mode == Mode.FAIL_SOFT) {
                log.info("Sync (soft) skipped for local {}: {}", localSubscriptionId, e.getMessage());
                return null;
            }
            throw e;
        }
        applyRazorpayState(sub, rzp);
        return subscriptions.save(sub);
    }

    /**
     * The status matrix — also invoked by RazorpayWebhookService's lifecycle cases
     * (its parsed payload fields alias this record's fields), so webhook and pull
     * cannot drift apart.
     */
    void applyRazorpayState(Subscription sub, RazorpayGateway.RazorpaySubscription rzp) {
        if (isAlreadySynced(sub, rzp.status())) {
            log.info("Local {} already matches Razorpay status {}", sub.getId(), rzp.status());
            return;
        }
        switch (rzp.status() == null ? "" : rzp.status()) {
            case "activated", "resumed", "active" -> {
                applyActivation(sub, rzp.planId(), rzp.currentStart(), rzp.currentEnd(), rzp.paymentMethod());
                log.info("Synced local {} (org {}) -> ACTIVE from Razorpay {}", sub.getId(), sub.getOrgId(), rzp.id());
            }
            case "pending" -> sub.setStatus(SubscriptionStatus.PAST_DUE);
            case "halted" -> sub.setStatus(SubscriptionStatus.HALTED);
            case "cancelled" -> sub.setStatus(SubscriptionStatus.CANCELLED);
            case "completed", "expired" -> sub.setStatus(SubscriptionStatus.EXPIRED);
            case "created", "authenticated" -> {
                // Checkout not settled yet (authorization seen at most) — stay PENDING, poll again.
                log.info("Razorpay subscription {} is {} — local {} stays PENDING", rzp.id(), rzp.status(), sub.getId());
            }
            default -> log.info("Unknown Razorpay status '{}' for {} — local {} untouched",
                    rzp.status(), rzp.id(), sub.getId());
        }
    }

    /** Activated/resumed shared entry — webhook service's activate() delegates here as well. */
    public void applyActivation(Subscription sub, String razorpayPlanId,
                                Long currentStartEpoch, Long currentEndEpoch, String paymentMethod) {
        sub.setStatus(SubscriptionStatus.ACTIVE);
        if (currentStartEpoch != null) {
            sub.setStartDate(Instant.ofEpochSecond(currentStartEpoch).atZone(ZoneOffset.UTC).toLocalDate());
        }
        if (currentEndEpoch != null) {
            sub.setEndDate(Instant.ofEpochSecond(currentEndEpoch).atZone(ZoneOffset.UTC).toLocalDate());
        }
        applyPlanMapping(sub, razorpayPlanId);
        sub.setPaymentMethod(paymentMethod);
        supersedeLiveTrial(sub);
    }

    /**
     * Keep the local plan/cycle in step with whichever Razorpay plan names itself.
     * Unknown ids (not in our catalogue) leave the row alone. Shared by the webhook
     * (subscription.updated / charged payloads) and by activation.
     */
    public void applyPlanMapping(Subscription sub, String razorpayPlanId) {
        if (razorpayPlanId == null) {
            return;
        }
        plans.findByRazorpayMonthlyPlanIdOrRazorpayYearlyPlanId(razorpayPlanId, razorpayPlanId)
                .ifPresent(plan -> {
                    BillingCycle cycle = razorpayPlanId.equals(plan.getRazorpayMonthlyPlanId())
                            ? BillingCycle.MONTHLY : BillingCycle.YEARLY;
                    if (!plan.getId().equals(sub.getPlanId()) || sub.getBillingCycle() != cycle) {
                        sub.setPlanId(plan.getId());
                        sub.setBillingCycle(cycle);
                        log.info("Subscription row {} now maps to plan {} ({} via Razorpay plan {})",
                                sub.getId(), plan.getName(), cycle, razorpayPlanId);
                    }
                });
    }

    /** Supersede a still-live trial row of the same org (trial ends early — it was free). */
    public void supersedeLiveTrial(Subscription activated) {
        subscriptions.findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(
                        activated.getOrgId(), EnumSet.of(SubscriptionStatus.TRIALING))
                .filter(trial -> !trial.getId().equals(activated.getId()))
                .ifPresent(trial -> {
                    trial.setStatus(SubscriptionStatus.CANCELLED);
                    trial.setEndDate(activationDate());
                    log.info("Trial row {} superseded ({} now ACTIVE, org {})",
                            trial.getId(), activated.getRazorpaySubscriptionId(), activated.getOrgId());
                });
    }

    /** UTC today — same "the trial ends the day it was superseded" convention the webhook uses. */
    private static LocalDate activationDate() {
        return LocalDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    private boolean isAlreadySynced(Subscription sub, String razorpayStatus) {
        SubscriptionStatus local = localStatusFor(razorpayStatus);
        return local != null && sub.getStatus() == local;
    }

    /** None if created/authenticated/unknown — those never match a "nothing to do" shortcut. */
    private static SubscriptionStatus localStatusFor(String razorpayStatus) {
        return switch (razorpayStatus == null ? "" : razorpayStatus) {
            case "activated", "resumed", "active" -> SubscriptionStatus.ACTIVE;
            case "pending" -> SubscriptionStatus.PAST_DUE;
            case "halted" -> SubscriptionStatus.HALTED;
            case "cancelled" -> SubscriptionStatus.CANCELLED;
            case "completed", "expired" -> SubscriptionStatus.EXPIRED;
            default -> null;
        };
    }

}
