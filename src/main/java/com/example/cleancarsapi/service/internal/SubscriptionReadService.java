package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.internal.InternalSubscriptionResponse;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Subscription lookups for the internal console. The live-status logic (only
 * TRIALING/ACTIVE/PAST_DUE count) lives here once and is reused by the org
 * endpoints and the per-user rows — plan expiry is read straight off the row's
 * {@code endDate}; no derived local expiry math anywhere.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionReadService {

    /** A subscription in one of these states counts as the org's current/live one. */
    private static final Set<SubscriptionStatus> LIVE = EnumSet.of(
            SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;

    /** The most recent LIVE subscription row of the org (with its plan), or empty. */
    @Transactional(readOnly = true)
    public Optional<LiveSubscription> liveForOrg(UUID orgId) {
        return subscriptions.findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(orgId, LIVE)
                .flatMap(sub -> plans.findById(sub.getPlanId()).map(plan -> new LiveSubscription(sub, plan)));
    }

    /** The org's live subscription (or null), in the console response shape. */
    @Transactional(readOnly = true)
    public InternalSubscriptionResponse currentForOrg(UUID orgId) {
        return liveForOrg(orgId)
                .map(live -> InternalSubscriptionResponse.of(live.subscription(), live.plan()))
                .orElse(null);
    }

    /** One row by id in the console shape (404 for unknown ids) — the sync endpoint's read-back. */
    @Transactional(readOnly = true)
    public InternalSubscriptionResponse rowForConsole(UUID subscriptionId) {
        Subscription sub = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new com.example.cleancarsapi.exception.NotFoundException(
                        "subscription", subscriptionId));
        return InternalSubscriptionResponse.of(sub,
                plans.findById(sub.getPlanId()).orElse(null));
    }

    /** Append-only history listing for the console — every row, terminal ones included. */
    @Transactional(readOnly = true)
    public Page<InternalSubscriptionResponse> searchAcrossOrgs(UUID orgId, SubscriptionStatus status, Pageable pageable) {
        return subscriptions.searchAcrossOrgs(orgId, status, pageable).map(sub ->
                InternalSubscriptionResponse.of(sub, plans.findById(sub.getPlanId()).orElse(null)));
    }

    /** The live subscription together with its plan. */
    public record LiveSubscription(Subscription subscription, SubscriptionPlan plan) {
    }
}
