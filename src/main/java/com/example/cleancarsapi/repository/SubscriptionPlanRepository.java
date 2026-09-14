package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /** Publicly listed plans for the pricing page, in display order (cheapest first on ties). */
    List<SubscriptionPlan> findByIsPublicTrueOrderBySortOrderAscMonthlyPriceAsc();

    /** The one dedicated, one-time-usable Trial plan — resolved automatically, never chosen by the caller. */
    Optional<SubscriptionPlan> findByIsTrialTrue();
}
