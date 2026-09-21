package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID> {

    List<ServiceOrderItem> findByServiceOrderIdOrderByCreatedAtAscIdAsc(UUID serviceOrderId);

    List<ServiceOrderItem> findByServiceOrderIdInOrderByCreatedAtAscIdAsc(Collection<UUID> serviceOrderIds);

    int deleteByServiceOrderId(UUID serviceOrderId);
}
