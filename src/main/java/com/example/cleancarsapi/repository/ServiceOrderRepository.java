package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    Optional<ServiceOrder> findByIdAndOrgId(Long id, Long orgId);

    /** A car's service history, newest first — for the car detail endpoint. */
    List<ServiceOrder> findByOrgIdAndCarIdOrderByCreatedAtDesc(long orgId, long carId);

    /** All-time, no date filter — e.g. "services in progress" on the dashboard. */
    long countByOrgIdAndStatus(long orgId, ServiceOrderStatus status);

    /** All-time, no date filter — outstanding orders, excluding voided (cancelled) ones. */
    long countByOrgIdAndPaidFalseAndStatusNot(long orgId, ServiceOrderStatus status);

    /**
     * Org-scoped listing. Optional filters: {@code status}, {@code paid}, and a
     * {@code search} term matched against the customer name or the car number.
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
                                and lower(cr.carNumber) like lower(concat('%', :search, '%'))))
            """)
    Page<ServiceOrder> search(@Param("orgId") long orgId,
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
    List<LocalDateTime> findCreatedAtForServiceCount(@Param("orgId") long orgId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("toExclusive") LocalDateTime toExclusive);

    /** For the TOTAL_REVENUE chart: one line per item on a paid order in range, bucketed by the order's date. */
    @Query("""
            select new com.example.cleancarsapi.dto.ChartRevenueLine(
                so.createdAt, i.basePrice, i.gstPercentage, i.gstIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = true
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<ChartRevenueLine> findPaidRevenueLines(@Param("orgId") long orgId,
                                                @Param("from") LocalDateTime from,
                                                @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Same shape as {@link #findPaidRevenueLines}, but for unpaid, non-cancelled orders —
     * the dashboard's "unpaid revenue" figure. Cancelled orders are excluded: a voided job
     * isn't money still owed.
     */
    @Query("""
            select new com.example.cleancarsapi.dto.ChartRevenueLine(
                so.createdAt, i.basePrice, i.gstPercentage, i.gstIncluded, i.quantity)
            from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
            where so.orgId = :orgId and so.paid = false
              and so.status <> com.example.cleancarsapi.entity.ServiceOrderStatus.CANCELLED
              and so.createdAt >= :from and so.createdAt < :toExclusive
            """)
    List<ChartRevenueLine> findUnpaidRevenueLines(@Param("orgId") long orgId,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("toExclusive") LocalDateTime toExclusive);
}
