package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Σ {@code saleNet} of AMCs sold (payment date) in {@code [from, to]}, both inclusive. */
    @Query("select coalesce(sum(s.saleNet), 0) from AmcSubscription s "
            + "where s.orgId = :orgId and s.paymentDate between :from and :to")
    BigDecimal sumSaleNetBetween(@Param("orgId") UUID orgId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** {@code [paymentDate, saleNet]} per AMC sold in {@code [from, to]}, both inclusive — the chart's AMC line. */
    @Query("select s.paymentDate, s.saleNet from AmcSubscription s "
            + "where s.orgId = :orgId and s.paymentDate between :from and :to")
    List<Object[]> findSaleNetByPaymentDateBetween(@Param("orgId") UUID orgId, @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /** {@code [planName, count, Σ saleNet]} per AMC plan sold in {@code [from, to]} (payment date), both inclusive. */
    @Query("select max(s.planName), count(s), coalesce(sum(s.saleNet), 0) from AmcSubscription s "
            + "where s.orgId = :orgId and s.paymentDate between :from and :to group by s.planId")
    List<Object[]> sumSalesByPlan(@Param("orgId") UUID orgId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every AMC the org ever sold — the usage chart derives its counts from these at runtime. */
    List<AmcSubscription> findByOrgId(UUID orgId);

    boolean existsByPlanId(UUID planId);

    boolean existsByVariantId(UUID variantId);
}
