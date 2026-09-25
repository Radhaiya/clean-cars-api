package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.EventProcessingStatus;
import com.example.cleancarsapi.entity.PaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    /** A stored event that was already handled (or is mid-handling) — this delivery is a duplicate. */
    boolean existsByRazorpayEventIdAndProcessingStatusIn(
            String razorpayEventId, Collection<EventProcessingStatus> statuses);

    /** The existing row for a FAILED event Razorpay is retrying (it is updated, not re-inserted). */
    Optional<PaymentEvent> findByRazorpayEventId(String razorpayEventId);

    /** Cross-org audit of subscription lifecycle events for the internal console (org via the local sub). */
    @Query("""
            select e from PaymentEvent e
              join Subscription s on s.razorpaySubscriptionId = e.razorpaySubscriptionId
            where (:orgId is null or s.orgId = :orgId)
            order by e.receivedAt desc
            """)
    Page<PaymentEvent> searchAcrossOrgs(@Param("orgId") UUID orgId, Pageable pageable);
}
