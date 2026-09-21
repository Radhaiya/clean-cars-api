package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.EventProcessingStatus;
import com.example.cleancarsapi.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    /** A stored event that was already handled (or is mid-handling) — this delivery is a duplicate. */
    boolean existsByRazorpayEventIdAndProcessingStatusIn(
            String razorpayEventId, Collection<EventProcessingStatus> statuses);

    /** The existing row for a FAILED event Razorpay is retrying (it is updated, not re-inserted). */
    Optional<PaymentEvent> findByRazorpayEventId(String razorpayEventId);
}
