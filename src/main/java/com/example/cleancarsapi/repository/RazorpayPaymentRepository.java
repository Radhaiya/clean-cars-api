package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.RazorpayPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RazorpayPaymentRepository extends JpaRepository<RazorpayPayment, UUID> {

    Optional<RazorpayPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    /** Cross-org payments for the internal console — org joins through the local subscription. */
    @Query("""
            select p from RazorpayPayment p
              join Subscription s on s.id = p.subscriptionId
            where (:orgId is null or s.orgId = :orgId)
            order by p.createdAt desc
            """)
    Page<RazorpayPayment> searchAcrossOrgs(@Param("orgId") UUID orgId, Pageable pageable);
}
