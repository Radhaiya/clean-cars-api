package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, Long> {

    List<ServiceOrderItem> findByServiceOrderIdOrderByIdAsc(long serviceOrderId);

    List<ServiceOrderItem> findByServiceOrderIdInOrderByIdAsc(Collection<Long> serviceOrderIds);

    int deleteByServiceOrderId(long serviceOrderId);
}
