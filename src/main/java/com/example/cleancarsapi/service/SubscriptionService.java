package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.StartTrialRequest;
import com.example.cleancarsapi.dto.StartTrialResponse;
import com.example.cleancarsapi.dto.SubscriptionResponse;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
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

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Subscription lifecycle actions (not the CRUD-four split). Right now: start a free
 * trial — which, for an org-less user, also creates their organization (1 user : 1 org).
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

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final JwtService jwtService;

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
                .map(sub -> {
                    SubscriptionPlan plan = plans.findById(sub.getPlanId())
                            .orElseThrow(() -> new NotFoundException("plan", sub.getPlanId()));
                    return CurrentSubscriptionResponse.of(sub, plan, TRIAL_DAYS);
                })
                .orElseGet(CurrentSubscriptionResponse::none);
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

        Organization org = new Organization();
        org.setName(request.orgName().trim());
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
