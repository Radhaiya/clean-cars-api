package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndServiceOrderIdAndOrgId(UUID id, UUID serviceOrderId, UUID orgId);

    /** An order's receipts in the order they happened: by date, then by when they were recorded. */
    List<Payment> findByServiceOrderIdOrderByPaymentDateAscCreatedAtAscIdAsc(UUID serviceOrderId);

    List<Payment> findByServiceOrderIdIn(Collection<UUID> serviceOrderIds);

    boolean existsByServiceOrderId(UUID serviceOrderId);

    @Modifying
    void deleteByServiceOrderId(UUID serviceOrderId);
}
