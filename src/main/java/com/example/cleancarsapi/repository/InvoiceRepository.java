package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByServiceOrderIdAndOrgId(UUID serviceOrderId, UUID orgId);

    /** The highest number handed out in the org so far (0 = none). */
    @Query("select coalesce(max(i.invoiceNumber), 0) from Invoice i where i.orgId = :orgId")
    int maxInvoiceNumber(@Param("orgId") UUID orgId);

    @Modifying
    void deleteByServiceOrderId(UUID serviceOrderId);
}
