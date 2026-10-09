package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmcInvoiceRepository extends JpaRepository<AmcInvoice, UUID> {

    Optional<AmcInvoice> findByAmcSubscriptionIdAndOrgId(UUID amcSubscriptionId, UUID orgId);

    /** Which of these AMC sales already have an invoice. */
    @Query("select i.amcSubscriptionId from AmcInvoice i where i.amcSubscriptionId in :subscriptionIds")
    List<UUID> findSubscriptionIdsWithInvoice(@Param("subscriptionIds") Collection<UUID> subscriptionIds);

    /** The highest AMC invoice number handed out in the org so far (0 = none). */
    @Query("select coalesce(max(i.invoiceNumber), 0) from AmcInvoice i where i.orgId = :orgId")
    int maxInvoiceNumber(@Param("orgId") UUID orgId);
}
