package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.dto.EmployeeJobCountRow;
import com.example.cleancarsapi.dto.RecentCustomerRow;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID>, JpaSpecificationExecutor<ServiceOrder> {

    Optional<ServiceOrder> findByIdAndOrgId(UUID id, UUID orgId);

    /** (subscriptionId, slotIndex) of every live (non-cancelled) AMC redemption — what "used" is derived from. */
    @Query("select so.amcSubscriptionId, so.amcSlotIndex from ServiceOrder so "
            + "where so.amcSubscriptionId in :ids and so.status <> :cancelled")
    List<Object[]> liveAmcSlots(@Param("ids") java.util.Collection<UUID> ids,
                                @Param("cancelled") ServiceOrderStatus cancelled);

    /** Clears the assignee on every job an employee was on (making their roster row deletable). */
    @Modifying
    @Query("update ServiceOrder so set so.employeeId = null where so.employeeId = :employeeId")
    int clearEmployeeAssignments(@Param("employeeId") UUID employeeId);

    /** A car's service history, newest first — for the car detail endpoint. */
    List<ServiceOrder> findByOrgIdAndCarIdOrderByCreatedAtDesc(UUID orgId, UUID carId);

    /** A bike's service history, newest first — for the bike detail endpoint. */
    List<ServiceOrder> findByOrgIdAndBikeIdOrderByCreatedAtDesc(UUID orgId, UUID bikeId);

    /** All-time, no date filter — e.g. "services in progress" on the dashboard. */
    long countByOrgIdAndStatus(UUID orgId, ServiceOrderStatus status);

    /** All-time job-card count per org — the internal console's per-org totals. */
    long countByOrgId(UUID orgId);

    /** In-progress split by vehicle kind for the dashboard: orders on a car, then on a bike. */
    long countByOrgIdAndStatusAndCarIdIsNotNull(UUID orgId, ServiceOrderStatus status);

    long countByOrgIdAndStatusAndBikeIdIsNotNull(UUID orgId, ServiceOrderStatus status);

    /** The dashboard's "today's services": every order created in the org-zone day, newest first, any status. */
    List<ServiceOrder> findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID orgId, LocalDateTime from, LocalDateTime toExclusive);

    /**
     * The dashboard's recently-served customers: each customer's most recent
     * non-cancelled order timestamp, newest first. Cancelled orders are not
     * "served".
     */
    @Query("""
            select new com.example.cleancarsapi.dto.RecentCustomerRow(
                so.customerId, max(so.createdAt))
            from ServiceOrder so
            where so.orgId = :orgId
              and so.status <> com.example.cleancarsapi.entity.ServiceOrderStatus.CANCELLED
            group by so.customerId
            order by max(so.createdAt) desc
            """)
    List<RecentCustomerRow> findRecentCustomerActivity(@Param("orgId") UUID orgId,
                                                       PageRequest pageable);

    /** All-time, no date filter — outstanding orders, excluding voided (cancelled) ones. */
    long countByOrgIdAndPaidFalseAndStatusNot(UUID orgId, ServiceOrderStatus status);

    /** The dashboard's unpaid book: every order still owing money (fully unpaid or part-paid), excluding voided ones. */
    List<ServiceOrder> findByOrgIdAndPaidFalseAndStatusNot(UUID orgId, ServiceOrderStatus status);

    /** Revenue-reporting fetch: every order created in range; {@code OrderRevenueReader} adds lines and payments. */
    List<ServiceOrder> findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID orgId, LocalDateTime from, LocalDateTime toExclusive);

    /** Every order the org ever created — the lifetime customer-revenue ranking. */
    List<ServiceOrder> findByOrgId(UUID orgId);

    /** Row-locking variant of {@link #findByIdAndOrgId} — payment writes serialize on the order so two can't both pass the overpayment check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select so from ServiceOrder so where so.id = :id and so.orgId = :orgId")
    Optional<ServiceOrder> lockByIdAndOrgId(@Param("id") UUID id, @Param("orgId") UUID orgId);

    /** For the TOTAL_SERVICE chart: one timestamp per non-cancelled order in range, bucketed in code. */
    @Query("""
            select so.createdAt from ServiceOrder so
            where so.orgId = :orgId
              and so.status <> com.example.cleancarsapi.entity.ServiceOrderStatus.CANCELLED
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<LocalDateTime> findCreatedAtForServiceCount(@Param("orgId") UUID orgId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Kpi-tiles' revenue-by-employee breakdown, job-count half: each employee's count of
     * non-{@code CANCELLED} job cards in range (same population as {@link
     * #findCreatedAtForServiceCount}), grouped including a null-{@code employeeId} row for
     * unassigned orders — {@code ChartService} labels that row "Unassigned".
     */
    @Query("""
            select new com.example.cleancarsapi.dto.EmployeeJobCountRow(so.employeeId, count(so))
            from ServiceOrder so
            where so.orgId = :orgId
              and so.status <> com.example.cleancarsapi.entity.ServiceOrderStatus.CANCELLED
              and so.createdAt >= :from and so.createdAt < :toExclusive
            group by so.employeeId
            """)
    List<EmployeeJobCountRow> findJobCountsByEmployee(@Param("orgId") UUID orgId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("toExclusive") LocalDateTime toExclusive);
}
