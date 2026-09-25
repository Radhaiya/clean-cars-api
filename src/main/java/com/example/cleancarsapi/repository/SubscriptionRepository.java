package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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

    /** TRIALING rows whose trial window has lapsed — flip to EXPIRED (see {@code SubscriptionExpiryJob}). */
    List<Subscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDate endDateBefore);

    /** Cross-org history for the internal console — optional org/status filters, newest first. */
    @Query("""
            select s from Subscription s
            where (:orgId is null or s.orgId = :orgId)
              and (:status is null or s.status = :status)
            order by s.createdAt desc
            """)
    Page<Subscription> searchAcrossOrgs(@Param("orgId") UUID orgId,
                                        @Param("status") SubscriptionStatus status,
                                        Pageable pageable);
}
