package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmcSubscriptionRepository extends JpaRepository<AmcSubscription, UUID> {

    Optional<AmcSubscription> findByIdAndOrgId(UUID id, UUID orgId);

    /** Row-locked fetch — serialises redemptions so two requests can't take the same slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AmcSubscription s where s.id = :id and s.orgId = :orgId")
    Optional<AmcSubscription> lockByIdAndOrgId(@Param("id") UUID id, @Param("orgId") UUID orgId);

    List<AmcSubscription> findByOrgIdAndCarIdOrderByStartDateDescCreatedAtDesc(UUID orgId, UUID carId);

    List<AmcSubscription> findByOrgIdAndBikeIdOrderByStartDateDescCreatedAtDesc(UUID orgId, UUID bikeId);

    /** Every sale of a plan, newest first — the plan detail's stats and active-sales list. */
    List<AmcSubscription> findByOrgIdAndPlanIdOrderByStartDateDescCreatedAtDesc(UUID orgId, UUID planId);

    boolean existsByPlanId(UUID planId);

    boolean existsByVariantId(UUID variantId);
}
