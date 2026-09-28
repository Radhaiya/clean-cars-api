package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.dto.EmployeeJobCountRow;
import com.example.cleancarsapi.dto.EmployeeRevenueRow;
import com.example.cleancarsapi.dto.PaymentTypeRevenueRow;
import com.example.cleancarsapi.dto.RecentCustomerRow;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

    Optional<ServiceOrder> findByIdAndOrgId(UUID id, UUID orgId);

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

    /** For kpi-tiles' range-scoped average order value — the same paid-order population as {@link #findPaidRevenueLines}. */
    long countByOrgIdAndPaidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID orgId, LocalDateTime from, LocalDateTime toExclusive);

    /**
     * Org-scoped listing. Optional filters: {@code status}, {@code paid}, and a
     * {@code search} term matched against the customer name or the vehicle
     * (car or bike) number.
     */
    @Query("""
            select so from ServiceOrder so
            where so.orgId = :orgId
              and (:status is null or so.status = :status)
              and (:paid is null or so.paid = :paid)
              and (:search is null
                   or exists (select 1 from Customer c
                              where c.id = so.customerId
                                and lower(c.name) like lower(concat('%', :search, '%')))
                   or exists (select 1 from Car cr
                              where cr.id = so.carId
                                and lower(cr.carNumber) like lower(concat('%', :search, '%')))
                   or exists (select 1 from Bike bk
                              where bk.id = so.bikeId
                                and lower(bk.bikeNumber) like lower(concat('%', :search, '%'))))
            """)
    Page<ServiceOrder> search(@Param("orgId") UUID orgId,
                              @Param("status") ServiceOrderStatus status,
                              @Param("paid") Boolean paid,
                              @Param("search") String search,
                              Pageable pageable);

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

    /** For the TOTAL_REVENUE chart: one line per item on a paid order in range, bucketed by the order's date. */
    @Query("""
            select new com.example.cleancarsapi.dto.ChartRevenueLine(
                so.createdAt, i.basePrice, i.taxPercentage, i.taxIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = true
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<ChartRevenueLine> findPaidRevenueLines(@Param("orgId") UUID orgId,
                                                @Param("from") LocalDateTime from,
                                                @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Same paid-orders-in-range population as {@link #findPaidRevenueLines}, but carrying
     * the order's {@code paymentType} instead of its {@code createdAt} — kpi-tiles uses this
     * one fetch for both {@code totalRevenue} and the revenue-by-payment-type breakdown.
     */
    @Query("""
            select new com.example.cleancarsapi.dto.PaymentTypeRevenueRow(
                so.paymentType, i.basePrice, i.taxPercentage, i.taxIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = true
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<PaymentTypeRevenueRow> findPaidRevenueLinesWithPaymentType(@Param("orgId") UUID orgId,
                                                                    @Param("from") LocalDateTime from,
                                                                    @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Same shape as {@link #findPaidRevenueLines}, but for unpaid, non-cancelled orders —
     * the dashboard's "unpaid revenue" figure. Cancelled orders are excluded: a voided job
     * isn't money still owed.
     */
    @Query("""
            select new com.example.cleancarsapi.dto.ChartRevenueLine(
                so.createdAt, i.basePrice, i.taxPercentage, i.taxIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = false
              and so.status <> com.example.cleancarsapi.entity.ServiceOrderStatus.CANCELLED
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<ChartRevenueLine> findUnpaidRevenueLines(@Param("orgId") UUID orgId,
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

    /**
     * Kpi-tiles' revenue-by-employee breakdown, revenue half: same paid-orders-in-range
     * population as {@link #findPaidRevenueLines}, keyed by the order's assigned employee
     * instead of its {@code createdAt} — {@code employeeId} is null for unassigned orders.
     */
    @Query("""
            select new com.example.cleancarsapi.dto.EmployeeRevenueRow(
                so.employeeId, i.basePrice, i.taxPercentage, i.taxIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = true
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<EmployeeRevenueRow> findPaidRevenueLinesByEmployee(@Param("orgId") UUID orgId,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("toExclusive") LocalDateTime toExclusive);
}
