package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ChartBucket;
import com.example.cleancarsapi.dto.ChartGranularity;
import com.example.cleancarsapi.dto.ChartMetric;
import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.dto.EmployeeJobCountRow;
import com.example.cleancarsapi.dto.EmployeeRevenueResponse;
import com.example.cleancarsapi.dto.EmployeeRevenueRow;
import com.example.cleancarsapi.dto.PaymentTypeRevenueRow;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.dto.KpiTilesResponse;
import com.example.cleancarsapi.dto.OrgTotalsResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import com.example.cleancarsapi.repository.BikeRepository;
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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final ServiceCatalogRepository serviceCatalog;
    private final ExpenseRepository expenses;
    private final PlanLimitService planLimits;

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
     * net of tax.
     */
    @Transactional(readOnly = true)
    public KpiTilesResponse getKpiTiles(UUID orgId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        enforceStatsRange(orgId, from);

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();

        List<PaymentTypeRevenueRow> paidLines =
                serviceOrders.findPaidRevenueLinesWithPaymentType(orgId, fromInclusive, toExclusive);
        BigDecimal totalRevenue = paidLines.stream()
                .map(ChartService::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> revenueByPaymentType = new LinkedHashMap<>();
        for (PaymentType type : PaymentType.values()) {
            revenueByPaymentType.put(type.name(), BigDecimal.ZERO);
        }
        revenueByPaymentType.put("UNCATEGORIZED", BigDecimal.ZERO);
        for (PaymentTypeRevenueRow line : paidLines) {
            String key = line.paymentType() == null ? "UNCATEGORIZED" : line.paymentType().name();
            revenueByPaymentType.merge(key, netAmount(line), BigDecimal::add);
        }

        List<Expense> expensesInRange = expenses.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                orgId, fromInclusive, toExclusive);
        BigDecimal totalExpenses = expensesInRange.stream()
                .map(ChartService::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> expensesByCategoryUnsorted = new HashMap<>();
        for (Expense expense : expensesInRange) {
            expensesByCategoryUnsorted.merge(expense.getCategoryName(), netAmount(expense), BigDecimal::add);
        }
        Map<String, BigDecimal> expensesByCategory = expensesByCategoryUnsorted.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        long totalServices = serviceOrders.findCreatedAtForServiceCount(orgId, fromInclusive, toExclusive).size();

        long paidOrderCount = serviceOrders.countByOrgIdAndPaidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                orgId, fromInclusive, toExclusive);
        BigDecimal averageServiceValue = paidOrderCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP);
        long newCustomers = customers.countByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                orgId, fromInclusive, toExclusive);

        List<EmployeeRevenueResponse> revenueByEmployee = employeePerformance(orgId, fromInclusive, toExclusive);

        return new KpiTilesResponse(totalRevenue, totalExpenses, totalRevenue.subtract(totalExpenses), totalServices,
                averageServiceValue, newCustomers, revenueByPaymentType, expensesByCategory, revenueByEmployee);
    }

    /**
     * Each employee's job count and paid revenue in range, ranked by revenue highest
     * first, plus one {@code employeeId: null, employeeName: "Unassigned"} row for
     * orders with no assigned employee. Job count and revenue are independent
     * populations (non-cancelled vs. paid orders), so a row can have one metric at zero.
     */
    private List<EmployeeRevenueResponse> employeePerformance(UUID orgId, LocalDateTime from, LocalDateTime toExclusive) {
        Map<UUID, Long> jobCounts = serviceOrders.findJobCountsByEmployee(orgId, from, toExclusive).stream()
                .collect(Collectors.toMap(EmployeeJobCountRow::employeeId, EmployeeJobCountRow::jobCount));

        Map<UUID, BigDecimal> revenue = new HashMap<>();
        for (EmployeeRevenueRow line : serviceOrders.findPaidRevenueLinesByEmployee(orgId, from, toExclusive)) {
            revenue.merge(line.employeeId(), netAmount(line), BigDecimal::add);
        }

        Set<UUID> employeeIds = new HashSet<>(jobCounts.keySet());
        employeeIds.addAll(revenue.keySet());
        if (employeeIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> assignedIds = employeeIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        Map<UUID, String> names = assignedIds.isEmpty() ? Map.of()
                : employees.findByOrgIdAndIdIn(orgId, assignedIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Employee::getName));

        return employeeIds.stream()
                .filter(id -> id == null || names.containsKey(id))
                .map(id -> new EmployeeRevenueResponse(id == null ? null : id.toString(),
                        id == null ? "Unassigned" : names.get(id),
                        jobCounts.getOrDefault(id, 0L), revenue.getOrDefault(id, BigDecimal.ZERO)))
                .sorted(Comparator.comparing(EmployeeRevenueResponse::totalRevenue, Comparator.reverseOrder())
                        .thenComparing(r -> r.employeeName() == null ? "" : r.employeeName()))
                .toList();
    }

    /** All-time org counts, no date range, no plan gating — see {@link OrgTotalsResponse}. */
    @Transactional(readOnly = true)
    public OrgTotalsResponse getTotals(UUID orgId) {
        return new OrgTotalsResponse(
                cars.countByOrgId(orgId),
                bikes.countByOrgId(orgId),
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
        return TaxBreakdown.of(line.basePrice(), line.taxPercentage(), line.taxIncluded())
                .times(line.quantity())
                .net();
    }

    private static BigDecimal netAmount(PaymentTypeRevenueRow line) {
        return TaxBreakdown.of(line.basePrice(), line.taxPercentage(), line.taxIncluded())
                .times(line.quantity())
                .net();
    }

    private static BigDecimal netAmount(EmployeeRevenueRow line) {
        return TaxBreakdown.of(line.basePrice(), line.taxPercentage(), line.taxIncluded())
                .times(line.quantity())
                .net();
    }

    private static BigDecimal netAmount(Expense expense) {
        return TaxBreakdown.of(expense.getAmount(), expense.getTaxPercentage(), expense.isTaxIncluded())
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
        planLimits.assertStatsRangeAllowed(orgId, from);
    }
}
