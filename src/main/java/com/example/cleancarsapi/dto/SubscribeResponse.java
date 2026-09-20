package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionPlan;

import java.time.LocalDate;

/**
 * Result of starting a paid subscription: the local row (status {@code PENDING} until
 * Razorpay's activation webhook lands) plus what the frontend needs to open Razorpay
 * Checkout — the Razorpay subscription id and our public key id.
 */
public record SubscribeResponse(
        Long subscriptionId,
        Long planId,
        String planName,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String razorpayPlanId,
        String razorpaySubscriptionId,
        String razorpayKeyId
) {
    public static SubscribeResponse of(
            Subscription s, SubscriptionPlan plan, String razorpayPlanId, String razorpayKeyId) {
        return new SubscribeResponse(
                s.getId(),
                s.getPlanId(),
                plan.getName(),
                s.getStatus().name(),
                s.getStartDate(),
                s.getEndDate(),
                razorpayPlanId,
                s.getRazorpaySubscriptionId(),
                razorpayKeyId);
    }
}
