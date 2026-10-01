package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcSubscriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AmcSubscriptionItemRepository extends JpaRepository<AmcSubscriptionItem, UUID> {

    List<AmcSubscriptionItem> findBySubscriptionIdInOrderByPositionAsc(Collection<UUID> subscriptionIds);
}
