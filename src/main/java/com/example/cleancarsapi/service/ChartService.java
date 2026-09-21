package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ChartBucket;
import com.example.cleancarsapi.dto.ChartGranularity;
import com.example.cleancarsapi.dto.ChartMetric;
import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.GstBreakdown;
import com.example.cleancarsapi.dto.KpiTilesResponse;
import com.example.cleancarsapi.dto.OrgTotalsResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ExpenseRepository;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * Time-bucketed dashboard metrics (not a CRUD resource, single class). Buckets are
 * calendar-aligned (see {@link ChartGranularity}) and always cover every period in
 * {@code [from, to]}, zero-filled where there's no data.
 */
@Service
@RequiredArgsConstructor
public class ChartService {

    private final ServiceOrderRepository serviceOrders;
    private final CarRepository cars;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final ServiceCatalogRepository serviceCatalog;
    private final ExpenseRepository expenses;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public List<ChartBucket> getBuckets(UUID orgId, ChartMetric metric, ChartGranularity granularity,
                                        LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        enforceStatsRange(orgId, from);

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        Map<LocalDate, BigDecimal> totals = switch (metric) {
            case TOTAL_SERVICE -> serviceCounts(orgId, granularity, fromInclusive, toExclusive);
            case TOTAL_REVENUE -> revenueTotals(orgId, granularity, fromInclusive, toExclusive);
        };
        return buildBuckets(from, to, granularity, totals);
    }

    /**
     * The dashboard's P&amp;L tile for {@code [from, to]} — real data (not mocked).
     * {@code totalRevenue}: same definition as {@code TOTAL_REVENUE} on {@link #getBuckets}
     * (paid orders only). {@code totalExpenses}: every {@code expenses} row in range (no
     * paid/unpaid concept there). {@code totalProfit = totalRevenue - totalExpenses}, both
     * net of GST.
     */
    @Transactional(readOnly = true)
    public KpiTilesResponse getKpiTiles(UUID orgId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        enforceStatsRange(orgId, from);

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();

        BigDecimal totalRevenue = serviceOrders.findPaidRevenueLines(orgId, fromInclusive, toExclusive).stream()
                .map(ChartService::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        orgId, fromInclusive, toExclusive).stream()
                .map(ChartService::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalServices = serviceOrders.findCreatedAtForServiceCount(orgId, fromInclusive, toExclusive).size();

        return new KpiTilesResponse(totalRevenue, totalExpenses, totalRevenue.subtract(totalExpenses), totalServices);
    }

    /** All-time org counts, no date range, no plan gating — see {@link OrgTotalsResponse}. */
    @Transactional(readOnly = true)
    public OrgTotalsResponse getTotals(UUID orgId) {
        return new OrgTotalsResponse(
                cars.countByOrgId(orgId),
                serviceCatalog.countByOrgId(orgId),
                employees.countByOrgId(orgId),
                customers.countByOrgId(orgId));
    }

    private Map<LocalDate, BigDecimal> serviceCounts(UUID orgId, ChartGranularity granularity,
                                                      LocalDateTime from, LocalDateTime toExclusive) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (LocalDateTime createdAt : serviceOrders.findCreatedAtForServiceCount(orgId, from, toExclusive)) {
            LocalDate key = bucketKey(createdAt.toLocalDate(), granularity);
            totals.merge(key, BigDecimal.ONE, BigDecimal::add);
        }
        return totals;
    }

    private Map<LocalDate, BigDecimal> revenueTotals(UUID orgId, ChartGranularity granularity,
                                                      LocalDateTime from, LocalDateTime toExclusive) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (ChartRevenueLine line : serviceOrders.findPaidRevenueLines(orgId, from, toExclusive)) {
            LocalDate key = bucketKey(line.orderCreatedAt().toLocalDate(), granularity);
            totals.merge(key, netAmount(line), BigDecimal::add);
        }
        return totals;
    }

    private static BigDecimal netAmount(ChartRevenueLine line) {
        return GstBreakdown.of(line.basePrice(), line.gstPercentage(), line.gstIncluded())
                .times(line.quantity())
                .net();
    }

    private static BigDecimal netAmount(Expense expense) {
        return GstBreakdown.of(expense.getAmount(), expense.getGstPercentage(), expense.isGstIncluded())
                .times(expense.getQuantity())
                .net();
    }

    private List<ChartBucket> buildBuckets(LocalDate from, LocalDate to, ChartGranularity granularity,
                                           Map<LocalDate, BigDecimal> totals) {
        List<ChartBucket> buckets = new ArrayList<>();
        LocalDate cursor = bucketKey(from, granularity);
        LocalDate untilKey = bucketKey(to, granularity);
        while (!cursor.isAfter(untilKey)) {
            LocalDate nextStart = step(cursor, granularity);
            LocalDate periodEnd = nextStart.minusDays(1);
            BigDecimal value = totals.getOrDefault(cursor, BigDecimal.ZERO);
            buckets.add(new ChartBucket(cursor, periodEnd, value));
            cursor = nextStart;
        }
        return buckets;
    }

    private static LocalDate bucketKey(LocalDate date, ChartGranularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private static LocalDate step(LocalDate bucketStart, ChartGranularity granularity) {
        return switch (granularity) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
            case YEAR -> bucketStart.plusYears(1);
        };
    }

    /** Statistics page hidden (stats_range_years = 0, or no live subscription) or {@code from} beyond the plan's range. */
    private void enforceStatsRange(UUID orgId, LocalDate from) {
        CurrentSubscriptionResponse subscription = subscriptionService.getCurrentForOrg(orgId);
        if (!subscription.active()) {
            throw ConflictException.statisticsNotAvailable();
        }
        Integer statsRangeYears = subscription.plan().features().statsRangeYears();
        if (statsRangeYears == null) {
            return;
        }
        if (statsRangeYears == 0) {
            throw ConflictException.statisticsNotAvailable();
        }
        LocalDate earliestAllowed = LocalDate.now().minusYears(statsRangeYears);
        if (from.isBefore(earliestAllowed)) {
            throw ConflictException.statsRangeExceeded(statsRangeYears);
        }
    }
}
