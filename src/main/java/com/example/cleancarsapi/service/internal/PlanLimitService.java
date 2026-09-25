package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One injectable gate for every plan capability check. Resolves org → live
 * subscription → plan (via {@link SubscriptionReadService}, LIVE = TRIALING /
 * ACTIVE / PAST_DUE, latest row) and answers plan-limit questions that fire at
 * write time and history-window reads. Hard-limit breaches throw coded
 * {@code ConflictException}s (409) so the UI can render an upgrade CTA.
 *
 * <p>{@code null} always means unlimited. {@code statsRangeYears = 0} means the
 * statistics feature is hidden entirely. Callable endpoints (invites, employee
 * creation) additionally require a live subscription — a terminal/absent row
 * blocks those even when the plan row would not.
 */
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final SubscriptionReadService subscriptionReadService;
    private final EmployeeRepository employees;

    /** Employee-row seat cap ({@code maxUsers}); the owner is not a seat. No live subscription also throws. */
    @Transactional(readOnly = true)
    public void assertCanAddUser(UUID orgId) {
        com.example.cleancarsapi.entity.SubscriptionPlan plan = currentPlan(orgId);
        if (plan == null) {
            throw ConflictException.orgNoLiveSubscription();
        }
        if (plan.getMaxUsers() != null && employees.countByOrgId(orgId) >= plan.getMaxUsers()) {
            throw ConflictException.userLimitReached(plan.getMaxUsers());
        }
    }

    /**
     * The live plan, or null when the org has none (no live subscription). The
     * grace/expiry nuance (PAST_DUE stays live, terminal rows don't) is delegated
     * to {@link SubscriptionReadService#liveForOrg} so every gate agrees.
     */
    @Transactional(readOnly = true)
    public com.example.cleancarsapi.entity.SubscriptionPlan currentPlan(UUID orgId) {
        return subscriptionReadService.liveForOrg(orgId)
                .map(live -> live.plan())
                .orElse(null);
    }

    /** Secret gate: statistics hidden (0 or no live subscription) or {@code from} earlier than allowed. */
    @Transactional(readOnly = true)
    public void assertStatsRangeAllowed(UUID orgId, LocalDate from) {
        com.example.cleancarsapi.entity.SubscriptionPlan plan = currentPlan(orgId);
        if (plan == null) {
            throw ConflictException.statisticsNotAvailable();
        }
        Integer statsRangeYears = plan.getStatsRangeYears();
        if (statsRangeYears == null) {
            return;
        }
        if (statsRangeYears == 0) {
            throw ConflictException.statisticsNotAvailable();
        }
        LocalDate earliestAllowed = LocalDate.now().minusYears(statsRangeYears);
        if (from.isBefore(earliestAllowed)) {
            throw ConflictException.statsRangeExceeded(statsRangeYears);
        }
    }
}
