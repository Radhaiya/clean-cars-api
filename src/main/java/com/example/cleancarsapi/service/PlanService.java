package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PlanResponse;
import com.example.cleancarsapi.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only lookup for the plan catalogue (single class, no CRUD split) — the
 * catalogue is global, not org-scoped.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private final SubscriptionPlanRepository plans;

    @Transactional(readOnly = true)
    public List<PlanResponse> listPublic() {
        return plans.findByIsPublicTrueOrderBySortOrderAscMonthlyPriceAsc()
                .stream().map(p -> PlanResponse.from(p, SubscriptionService.TRIAL_DAYS)).toList();
    }
}
