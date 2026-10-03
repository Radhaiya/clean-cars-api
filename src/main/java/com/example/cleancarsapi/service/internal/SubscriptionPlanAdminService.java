package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.internal.SubscriptionPlanRequest;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Console CRUD over the {@code subscription_plans} catalogue. Codes:
 * {@code plan_razorpay_id_exists}, {@code plan_has_subscriptions}, {@code trial_plan_protected}.
 * Capability edits apply to orgs on the plan immediately (limits are read live).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanAdminService {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionRepository subscriptions;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> list() {
        return plans.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan get(UUID id) {
        return find(id);
    }

    public long subscriptionCount(UUID id) {
        return subscriptions.countByPlanId(id);
    }

    @Transactional
    public SubscriptionPlan create(SubscriptionPlanRequest r, String actorEmail) {
        String monthly = blankToNull(r.razorpayMonthlyPlanId());
        String yearly = blankToNull(r.razorpayYearlyPlanId());
        checkRazorpayIds(null, monthly, yearly);
        SubscriptionPlan plan = plans.save(SubscriptionPlan.create(r.name().trim(), monthly, yearly, r.maxUsers(),
                r.maxCars(), r.reportWindowMonths(), r.statsRangeYears(), r.invoiceGeneration(), r.amcEnabled(),
                r.isPublic(), r.sortOrder()));
        log.info("Plan {} created by {}", plan.getId(), actorEmail);
        return plan;
    }

    @Transactional
    public SubscriptionPlan update(UUID id, SubscriptionPlanRequest r, String actorEmail) {
        SubscriptionPlan plan = find(id);
        String monthly = blankToNull(r.razorpayMonthlyPlanId());
        String yearly = blankToNull(r.razorpayYearlyPlanId());
        checkRazorpayIds(id, monthly, yearly);
        plan.apply(r.name().trim(), monthly, yearly, r.maxUsers(), r.maxCars(), r.reportWindowMonths(),
                r.statsRangeYears(), r.invoiceGeneration(), r.amcEnabled(), r.isPublic(), r.sortOrder());
        plans.flush();
        log.info("Plan {} updated by {}", id, actorEmail);
        return plan;
    }

    @Transactional
    public void delete(UUID id, String actorEmail) {
        SubscriptionPlan plan = find(id);
        if (plan.isTrial()) {
            throw ConflictException.trialPlanProtected();
        }
        if (subscriptions.countByPlanId(id) > 0) {
            throw ConflictException.planHasSubscriptions();
        }
        plans.delete(plan);
        log.info("Plan {} deleted by {}", id, actorEmail);
    }

    private SubscriptionPlan find(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Plan", id));
    }

    /** Razorpay ids are unique across plans (and across the two cycles of one plan). */
    private void checkRazorpayIds(UUID selfId, String monthly, String yearly) {
        for (String rzpId : new String[]{monthly, yearly}) {
            if (rzpId == null) {
                continue;
            }
            plans.findByRazorpayMonthlyPlanIdOrRazorpayYearlyPlanId(rzpId, rzpId)
                    .filter(other -> !other.getId().equals(selfId))
                    .ifPresent(other -> {
                        throw ConflictException.planRazorpayIdExists(rzpId);
                    });
        }
        if (monthly != null && monthly.equals(yearly)) {
            throw ConflictException.planRazorpayIdExists(monthly);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
