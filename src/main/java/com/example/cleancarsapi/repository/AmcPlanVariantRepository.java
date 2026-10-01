package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcPlanVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmcPlanVariantRepository extends JpaRepository<AmcPlanVariant, UUID> {

    Optional<AmcPlanVariant> findByIdAndPlanIdAndOrgId(UUID id, UUID planId, UUID orgId);

    List<AmcPlanVariant> findByPlanIdInOrderByTenureMonthsAscIntervalMonthsAsc(Collection<UUID> planIds);

    Optional<AmcPlanVariant> findByIdAndOrgId(UUID id, UUID orgId);

    List<AmcPlanVariant> findByPlanId(UUID planId);

    boolean existsByPlanIdAndTenureMonthsAndIntervalMonths(UUID planId, int tenureMonths, int intervalMonths);

    boolean existsByPlanIdAndTenureMonthsAndIntervalMonthsAndIdNot(UUID planId, int tenureMonths, int intervalMonths, UUID id);
}
