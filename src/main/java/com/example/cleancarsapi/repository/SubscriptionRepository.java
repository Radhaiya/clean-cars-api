package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** True if the org already has a "live" subscription (blocks starting another / a trial). */
    boolean existsByOrgIdAndStatusIn(long orgId, Collection<SubscriptionStatus> statuses);

    /** The org's current live subscription (most recent if data ever has more than one). */
    Optional<Subscription> findFirstByOrgIdAndStatusInOrderByCreatedAtDesc(
            long orgId, Collection<SubscriptionStatus> statuses);
}
