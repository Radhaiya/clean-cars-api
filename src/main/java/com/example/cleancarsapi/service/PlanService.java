package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PlanResponse;
import com.example.cleancarsapi.entity.SubscriptionPlan;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Read-only lookup for the plan catalogue (single class, no CRUD split) — the
 * catalogue is global, not org-scoped. Prices are fetched live from Razorpay
 * per plan (one call per offered cycle): Razorpay is the single pricing source
 * of truth, so an unreachable Razorpay fails the request rather than serving
 * stale prices.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlanService {

    private final SubscriptionPlanRepository plans;
    private final RazorpayGateway razorpay;

    /**
     * @see PlanResponse for the shape (cycle options, limits, features).
     * <p>Owner-only: the catalogue carries per-seat pricing scoped to the caller's
     * next purchase — invited members (managers/workers) have no plan decision to
     * make and get 403 (see docs/FEATURE-INVITES.md).
     */
    @Transactional(readOnly = true)
    public List<PlanResponse> listPublic() {
        AuthContext.require(UserRole.OWNER);
        return plans.findByIsPublicTrueOrderBySortOrderAsc()
                .stream().map(p -> PlanResponse.from(p, SubscriptionService.TRIAL_DAYS, pricing(p))).toList();
    }

    /** Live Razorpay pricing for whichever cycles the plan sells; trial plans (no Razorpay plan) get none. */
    private PlanResponse.Pricing pricing(SubscriptionPlan p) {
        if (!StringUtils.hasText(p.getRazorpayMonthlyPlanId()) && !StringUtils.hasText(p.getRazorpayYearlyPlanId())) {
            return null;
        }
        log.debug("Fetching Razorpay pricing for plan {}: monthly={} yearly={}",
                p.getId(), p.getRazorpayMonthlyPlanId(), p.getRazorpayYearlyPlanId());
        PlanResponse.Cycle monthly = p.getRazorpayMonthlyPlanId() == null
                ? null : cycle(p.getRazorpayMonthlyPlanId());
        PlanResponse.Cycle yearly = p.getRazorpayYearlyPlanId() == null
                ? null : cycle(p.getRazorpayYearlyPlanId());
        return new PlanResponse.Pricing(monthly, yearly);
    }

    private PlanResponse.Cycle cycle(String razorpayPlanId) {
        RazorpayGateway.RazorpayPlan plan = razorpay.fetchPlan(razorpayPlanId);
        return new PlanResponse.Cycle(razorpayPlanId, plan.amountRupees(), plan.item().currency());
    }
}
