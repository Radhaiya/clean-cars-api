package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /** Publicly listed plans for the pricing page, in display order. */
    List<SubscriptionPlan> findByIsPublicTrueOrderBySortOrderAsc();

    /** The one dedicated, one-time-usable Trial plan — resolved automatically, never chosen by the caller. */
    Optional<SubscriptionPlan> findByIsTrialTrue();

    /** The plan a Razorpay webhook references (either of its two cycle plan IDs matches). */
    Optional<SubscriptionPlan> findByRazorpayMonthlyPlanIdOrRazorpayYearlyPlanId(String monthly, String yearly);
}
