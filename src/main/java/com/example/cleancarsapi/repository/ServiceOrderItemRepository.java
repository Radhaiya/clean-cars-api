package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID> {

    List<ServiceOrderItem> findByServiceOrderIdOrderByCreatedAtAscIdAsc(UUID serviceOrderId);

    List<ServiceOrderItem> findByServiceOrderIdInOrderByCreatedAtAscIdAsc(Collection<UUID> serviceOrderIds);

    /** Every line named {@code name} (ignoring case) on the org's orders that aren't {@code excluded}, with its order's creation time. */
    @Query("""
            select i, o.createdAt from ServiceOrderItem i, ServiceOrder o
            where i.serviceOrderId = o.id and o.orgId = :orgId and o.status <> :excluded
              and lower(i.serviceName) = lower(:name)
            """)
    List<Object[]> findLinesByServiceName(@Param("orgId") UUID orgId, @Param("name") String name,
                                          @Param("excluded") ServiceOrderStatus excluded);

    boolean existsByServiceOrderId(UUID serviceOrderId);

    int deleteByServiceOrderId(UUID serviceOrderId);
}
