package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.dto.DashboardRecentCustomer;
import com.example.cleancarsapi.dto.DashboardResponse;
import com.example.cleancarsapi.dto.DashboardResponse.MonthlyFinancial;
import com.example.cleancarsapi.dto.DashboardResponse.RevenueSplit;
import com.example.cleancarsapi.dto.DashboardServiceRow;
import com.example.cleancarsapi.dto.RecentCustomerRow;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.BikeModel;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.CarModel;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.repository.BikeModelRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.ExpenseRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The org's home-dashboard snapshot (single class, not a CRUD resource, not gated by
 * any plan limit — unlike the {@code stats_range_years}-gated charts/KPI endpoints,
 * this is the always-available operational overview) plus the monthly
 * earnings-vs-expenses feed behind the earnings chart's year picker. See
 * {@link DashboardResponse}.
 *
 * <p>All day/month/window boundaries are drawn in the caller's org timezone
 * ({@code organizations.timezone}; UTC for org-less callers) and translated to UTC
 * instants for querying, since {@code created_at} is stored UTC.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** MySQL TIMESTAMP columns span 1970 .. 2038 — query bounds are clamped into it. */
    private static final LocalDateTime UTC_MIN = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime UTC_MAX = LocalDateTime.of(2038, 1, 19, 3, 14, 7);

    private final ServiceOrderRepository serviceOrders;
    private final ServiceOrderItemRepository orderItems;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CarModelRepository carModels;
    private final BikeModelRepository bikeModels;
    private final CustomerRepository customers;
    private final ExpenseRepository expenses;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public DashboardResponse get(UUID orgId) {
        ZoneId zone = orgTimezones.zone();
        LocalDateTime now = orgTimezones.now();
        LocalDate today = now.toLocalDate();

        long servicesInProgress = serviceOrders.countByOrgIdAndStatus(orgId, ServiceOrderStatus.IN_PROGRESS);
        long servicesUnpaid = serviceOrders.countByOrgIdAndPaidFalseAndStatusNot(orgId, ServiceOrderStatus.CANCELLED);
        long carsInProgress = serviceOrders.countByOrgIdAndStatusAndCarIdIsNotNull(orgId, ServiceOrderStatus.IN_PROGRESS);
        long bikesInProgress = serviceOrders.countByOrgIdAndStatusAndBikeIdIsNotNull(orgId, ServiceOrderStatus.IN_PROGRESS);

        BigDecimal unpaidAmount = sumUnpaidGross(orgId, today, zone);

        RevenueSplit todayRevenue = revenueSplit(orgId, today, zone);
        RevenueSplit yesterdayRevenue = revenueSplit(orgId, today.minusDays(1), zone);
        BigDecimal todayRevenueChangePct = percentChange(sum(todayRevenue), sum(yesterdayRevenue));

        long totalCustomers = customers.countByOrgId(orgId);
        long newCustomersLast30Days = customers.countByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                orgId, toUtc(now.minusDays(30), zone), toUtc(now, zone));
        long newCustomersPrevious30Days = customers.countByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                orgId, toUtc(now.minusDays(60), zone), toUtc(now.minusDays(30), zone));
        BigDecimal customersChangePct = percentChange(
                BigDecimal.valueOf(newCustomersLast30Days), BigDecimal.valueOf(newCustomersPrevious30Days));

        return new DashboardResponse(
                servicesInProgress,
                carsInProgress,
                bikesInProgress,
                servicesUnpaid,
                unpaidAmount,
                todayRevenue,
                yesterdayRevenue,
                todayRevenueChangePct,
                totalCustomers,
                newCustomersLast30Days,
                newCustomersPrevious30Days,
                customersChangePct,
                monthlyFinancials(orgId, today.getYear(), now, zone),
                todaysServices(orgId, now, zone),
                recentCustomers(orgId));
    }

    /**
     * Month-by-month earnings vs expenses for {@code year} — the earnings chart's
     * double bars (see {@link MonthlyFinancial}). Ungated by plan limits, but the
     * year is loosely bounded so absurd values can't reach SQL.
     */
    @Transactional(readOnly = true)
    public List<MonthlyFinancial> getEarnings(UUID orgId, int year) {
        if (year < 1900 || year > 2100) {
            throw new BadRequestException("'year' must be between 1900 and 2100");
        }
        ZoneId zone = orgTimezones.zone();
        return monthlyFinancials(orgId, year, orgTimezones.now(), zone);
    }

    // ----- assembly helpers -----

    private RevenueSplit revenueSplit(UUID orgId, LocalDate day, ZoneId zone) {
        LocalDateTime from = toUtc(day.atStartOfDay(), zone);
        LocalDateTime toExclusive = toUtc(day.plusDays(1).atStartOfDay(), zone);
        BigDecimal paid = sumNet(serviceOrders.findPaidRevenueLines(orgId, from, toExclusive));
        BigDecimal unpaid = sumNet(serviceOrders.findUnpaidRevenueLines(orgId, from, toExclusive));
        return new RevenueSplit(paid, unpaid);
    }

    /**
     * The unpaid book: sum of {@code grossTotal} across unpaid, non-cancelled orders,
     * org-wide (cancelled voided jobs aren't money owed). Bounded to the last 10 years
     * — {@code createdAt} is server-stamped, so no real order can fall outside.
     */
    private BigDecimal sumUnpaidGross(UUID orgId, LocalDate today, ZoneId zone) {
        List<ChartRevenueLine> lines = serviceOrders.findUnpaidRevenueLines(
                orgId, toUtc(today.minusYears(10).atStartOfDay(), zone), toUtc(today.plusDays(1).atStartOfDay(), zone));
        BigDecimal total = TaxBreakdown.zero().gross();
        for (ChartRevenueLine line : lines) {
            total = total.add(gross(line));
        }
        return total;
    }

    /**
     * Twelve calendar months of {@code year}: paid revenue net of tax and expense
     * gross tax-inclusive. Months after the current one (when the year is the
     * current one) are {@code null} regardless of any stray future-dated data.
     */
    private List<MonthlyFinancial> monthlyFinancials(UUID orgId, int year, LocalDateTime now, ZoneId zone) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDateTime from = toUtc(yearStart.atStartOfDay(), zone);
        LocalDateTime toExclusive = toUtc(yearStart.plusYears(1).atStartOfDay(), zone);

        Map<Integer, BigDecimal> earnings = new HashMap<>();
        for (ChartRevenueLine line : serviceOrders.findPaidRevenueLines(orgId, from, toExclusive)) {
            earnings.merge(orgMonth(line.orderCreatedAt(), zone), netAmount(line), BigDecimal::add);
        }
        Map<Integer, BigDecimal> expenseTotals = new HashMap<>();
        for (Expense expense : expenses.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(orgId, from, toExclusive)) {
            expenseTotals.merge(orgMonth(expense.getCreatedAt(), zone), grossOf(expense), BigDecimal::add);
        }

        int currentYear = now.toLocalDate().getYear();
        int nullAfter = year < currentYear ? 12 : year == currentYear ? now.toLocalDate().getMonthValue() : 0;
        List<MonthlyFinancial> result = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            BigDecimal earning = month > nullAfter ? null : earnings.getOrDefault(month, TaxBreakdown.zero().net());
            BigDecimal expense = month > nullAfter ? null : expenseTotals.getOrDefault(month, TaxBreakdown.zero().net());
            result.add(new MonthlyFinancial(month, earning, expense));
        }
        return result;
    }

    /** Today's job cards in the org zone, newest first, any status — see {@link DashboardServiceRow}. */
    private List<DashboardServiceRow> todaysServices(UUID orgId, LocalDateTime now, ZoneId zone) {
        LocalDate today = now.toLocalDate();
        List<ServiceOrder> orders = serviceOrders
                .findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        orgId, toUtc(today.atStartOfDay(), zone), toUtc(today.plusDays(1).atStartOfDay(), zone));
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ServiceOrderItem>> itemsByOrder = orderItems
                .findByServiceOrderIdInOrderByCreatedAtAscIdAsc(orders.stream().map(ServiceOrder::getId).toList())
                .stream().collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));

        Map<UUID, Car> carsById = cars.findByOrgIdAndIdIn(orgId,
                orders.stream().map(ServiceOrder::getCarId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Car::getId, Function.identity()));
        Map<UUID, Bike> bikesById = bikes.findByOrgIdAndIdIn(orgId,
                orders.stream().map(ServiceOrder::getBikeId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Bike::getId, Function.identity()));

        Map<UUID, String> carModelNames = modelNames(carModels.findAllById(
                carsById.values().stream().map(Car::getModelId).filter(Objects::nonNull).collect(Collectors.toSet())),
                CarModel::getId, CarModel::getName);
        Map<UUID, String> bikeModelNames = modelNames(bikeModels.findAllById(
                bikesById.values().stream().map(Bike::getModelId).filter(Objects::nonNull).collect(Collectors.toSet())),
                BikeModel::getId, BikeModel::getName);

        Map<UUID, Customer> customersById = customers.findByOrgIdAndIdIn(orgId,
                orders.stream().map(ServiceOrder::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<DashboardServiceRow> rows = new ArrayList<>(orders.size());
        for (ServiceOrder order : orders) {
            List<ServiceOrderItem> lines = itemsByOrder.getOrDefault(order.getId(), List.of());
            Car car = carsById.get(order.getCarId());
            Bike bike = bikesById.get(order.getBikeId());
            Customer customer = customersById.get(order.getCustomerId());
            rows.add(new DashboardServiceRow(
                    order.getId().toString(),
                    car != null ? car.getCarNumber() : bike == null ? null : bike.getBikeNumber(),
                    car != null ? carModelNames.get(car.getModelId()) : bike == null ? null : bikeModelNames.get(bike.getModelId()),
                    order.getCustomerId().toString(),
                    customer == null ? null : customer.getName(),
                    customer == null ? null : customer.getPhone(),
                    lines.isEmpty() ? null : lines.getFirst().getServiceName(),
                    lines.size(),
                    order.getNotes(),
                    order.getStatus(),
                    grossTotal(lines)));
        }
        return rows;
    }

    /** The 4 most recently served customers by their latest non-cancelled order. */
    private List<DashboardRecentCustomer> recentCustomers(UUID orgId) {
        List<RecentCustomerRow> activity = serviceOrders.findRecentCustomerActivity(orgId, PageRequest.of(0, 4));
        if (activity.isEmpty()) {
            return List.of();
        }
        Map<UUID, Customer> byId = customers.findByOrgIdAndIdIn(orgId,
                        activity.stream().map(RecentCustomerRow::customerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        return activity.stream()
                .filter(row -> byId.containsKey(row.customerId()))
                .map(row -> {
                    Customer customer = byId.get(row.customerId());
                    return new DashboardRecentCustomer(row.customerId().toString(), customer.getName(),
                            customer.getPhone(), row.lastServiceAt());
                })
                .toList();
    }

    // ----- small static helpers -----

    private static <T> Map<UUID, String> modelNames(List<T> models, Function<T, UUID> id, Function<T, String> name) {
        return models.stream().collect(Collectors.toMap(id, name));
    }

    private static BigDecimal grossTotal(List<ServiceOrderItem> lines) {
        BigDecimal total = TaxBreakdown.zero().gross();
        for (ServiceOrderItem item : lines) {
            total = total.add(TaxBreakdown.of(item.getBasePrice(), item.getTaxPercentage(), item.isTaxIncluded())
                    .times(item.getQuantity()).gross());
        }
        return total;
    }

    private static BigDecimal grossOf(Expense expense) {
        return TaxBreakdown.of(expense.getAmount(), expense.getTaxPercentage(), expense.isTaxIncluded())
                .times(expense.getQuantity())
                .gross();
    }

    private static BigDecimal sumNet(List<ChartRevenueLine> lines) {
        BigDecimal total = TaxBreakdown.zero().net();
        for (ChartRevenueLine line : lines) {
            total = total.add(netAmount(line));
        }
        return total;
    }

    private static BigDecimal netAmount(ChartRevenueLine line) {
        return TaxBreakdown.of(line.basePrice(), line.taxPercentage(), line.taxIncluded())
                .times(line.quantity())
                .net();
    }

    private static BigDecimal gross(ChartRevenueLine line) {
        return TaxBreakdown.of(line.basePrice(), line.taxPercentage(), line.taxIncluded())
                .times(line.quantity())
                .gross();
    }

    private static BigDecimal sum(RevenueSplit split) {
        return split.paid().add(split.unpaid());
    }

    /**
     * Day-over-day / window-over-window change in percent, 1dp — real math
     * (including a fall to zero = −100) whenever the base is positive; null when
     * the base is zero (division by zero is meaningless) — the client renders "—".
     */
    private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    /** Bucket key: the UTC-stored timestamp in the org zone's calendar month. */
    private static int orgMonth(LocalDateTime createdAtUtc, ZoneId zone) {
        return createdAtUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).getMonthValue();
    }

    /** Org-zone wall time → the UTC wall time stored in the DB, clamped to TIMESTAMP's range. */
    private static LocalDateTime toUtc(LocalDateTime orgWall, ZoneId zone) {
        LocalDateTime utc = orgWall.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if (utc.isBefore(UTC_MIN)) {
            return UTC_MIN;
        }
        if (utc.isAfter(UTC_MAX)) {
            return UTC_MAX;
        }
        return utc;
    }
}
