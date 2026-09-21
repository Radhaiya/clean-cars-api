package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** True if the org already has a "live" subscription (blocks starting another / a trial). */
    boolean existsByOrgIdAndStatusIn(UUID orgId, Collection<SubscriptionStatus> statuses);

    /** The org's current live subscription (most recent if data ever has more than one). */
    Optional<Subscription> findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(
            UUID orgId, Collection<SubscriptionStatus> statuses);

    /** The org's most recent subscription of any status (fallback so terminal states still report). */
    Optional<Subscription> findFirstByOrgIdOrderByCreatedAtDesc(UUID orgId);

    /** The local subscription a Razorpay webhook refers to (by its Razorpay subscription id). */
    Optional<Subscription> findByRazorpaySubscriptionId(String razorpaySubscriptionId);
}
