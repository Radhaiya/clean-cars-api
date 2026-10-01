package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AmcPlanItemRepository extends JpaRepository<AmcPlanItem, UUID> {

    List<AmcPlanItem> findByPlanIdOrderByPositionAsc(UUID planId);

    List<AmcPlanItem> findByPlanIdInOrderByPositionAsc(Collection<UUID> planIds);
}
