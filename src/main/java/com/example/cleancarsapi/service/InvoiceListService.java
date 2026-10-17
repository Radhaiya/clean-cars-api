package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.InvoiceListItem;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.AmcInvoice;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.entity.Invoice;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.repository.AmcInvoiceRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.InvoiceRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The org-wide invoice list: every service-order invoice plus (when the plan has AMC) every AMC-sale invoice,
 * resolved to customer / vehicle / live gross total with a handful of batched lookups, then filtered, sorted
 * and paged in memory — an org's invoices are a small, bounded set.
 */
@Service
@RequiredArgsConstructor
public class InvoiceListService {

    private static final int MAX_SIZE = 100;

    private final InvoiceRepository invoices;
    private final AmcInvoiceRepository amcInvoices;
    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final AmcSubscriptionRepository subscriptions;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final CustomerRepository customers;
    private final PlanLimitService planLimits;

    /**
     * @param type   {@code SERVICE}, {@code AMC} or null/blank for both
     * @param sort   {@code date} (default), {@code amount} or {@code number}
     * @param asc    sort ascending; default is newest / biggest first
     */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceListItem> list(UUID orgId, String search, String type, LocalDate from, LocalDate to,
                                              String sort, boolean asc, int page, int size) {
        boolean wantService = type == null || type.isBlank() || type.equalsIgnoreCase("SERVICE");
        boolean wantAmc = (type == null || type.isBlank() || type.equalsIgnoreCase("AMC")) && planLimits.isAmcEnabled(orgId);

        List<InvoiceListItem> rows = new ArrayList<>();
        if (wantService) {
            rows.addAll(serviceRows(orgId));
        }
        if (wantAmc) {
            rows.addAll(amcRows(orgId));
        }

        String needle = search == null ? "" : search.trim().toLowerCase();
        String needleDigits = needle.replaceAll("\\D", "");
        List<InvoiceListItem> filtered = rows.stream()
                .filter(r -> from == null || !r.invoiceDate().isBefore(from))
                .filter(r -> to == null || !r.invoiceDate().isAfter(to))
                .filter(r -> needle.isEmpty() || matches(r, needle, needleDigits))
                .sorted(comparator(sort, asc))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int safePage = Math.max(page, 0);
        int start = (int) Math.min((long) safePage * safeSize, filtered.size());
        int end = Math.min(start + safeSize, filtered.size());
        int totalPages = (int) Math.ceil(filtered.size() / (double) safeSize);
        return new PageResponse<>(filtered.subList(start, end), safePage, safeSize, filtered.size(), totalPages,
                end >= filtered.size());
    }

    private List<InvoiceListItem> serviceRows(UUID orgId) {
        List<Invoice> all = invoices.findByOrgId(orgId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, ServiceOrder> orderById = byId(orders.findAllById(
                all.stream().map(Invoice::getServiceOrderId).toList()), ServiceOrder::getId);
        Map<UUID, BigDecimal> totals = items.findByServiceOrderIdInOrderByCreatedAtAscIdAsc(orderById.keySet()).stream()
                .collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId,
                        Collectors.reducing(TaxBreakdown.zero(), TaxBreakdown::ofLine, TaxBreakdown::plus)))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().gross()));
        Map<UUID, Customer> customerById = customersOf(orgId,
                orderById.values().stream().map(ServiceOrder::getCustomerId).toList());
        Map<UUID, Car> carById = byId(cars.findByOrgIdAndIdIn(orgId, ids(orderById.values(), ServiceOrder::getCarId)), Car::getId);
        Map<UUID, Bike> bikeById = byId(bikes.findByOrgIdAndIdIn(orgId, ids(orderById.values(), ServiceOrder::getBikeId)), Bike::getId);

        List<InvoiceListItem> rows = new ArrayList<>();
        for (Invoice inv : all) {
            ServiceOrder o = orderById.get(inv.getServiceOrderId());
            if (o == null) {
                continue;
            }
            Customer c = customerById.get(o.getCustomerId());
            Car car = o.getCarId() == null ? null : carById.get(o.getCarId());
            Bike bike = o.getBikeId() == null ? null : bikeById.get(o.getBikeId());
            rows.add(new InvoiceListItem(inv.getId(), "SERVICE", inv.getInvoiceNumber(), inv.getInvoiceDate(),
                    inv.getServiceOrderId(), null, c == null ? null : c.getName(), c == null ? null : c.getPhone(),
                    car != null ? car.getCarNumber() : bike != null ? bike.getBikeNumber() : null,
                    totals.getOrDefault(o.getId(), BigDecimal.ZERO.setScale(2)), inv.getCreatedAt()));
        }
        return rows;
    }

    private List<InvoiceListItem> amcRows(UUID orgId) {
        List<AmcInvoice> all = amcInvoices.findByOrgId(orgId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, AmcSubscription> subById = byId(subscriptions.findAllById(
                all.stream().map(AmcInvoice::getAmcSubscriptionId).toList()), AmcSubscription::getId);
        Map<UUID, Car> carById = byId(cars.findByOrgIdAndIdIn(orgId, ids(subById.values(), AmcSubscription::getCarId)), Car::getId);
        Map<UUID, Bike> bikeById = byId(bikes.findByOrgIdAndIdIn(orgId, ids(subById.values(), AmcSubscription::getBikeId)), Bike::getId);
        List<UUID> customerIds = new ArrayList<>();
        carById.values().forEach(c -> customerIds.add(c.getCustomerId()));
        bikeById.values().forEach(b -> customerIds.add(b.getCustomerId()));
        Map<UUID, Customer> customerById = customersOf(orgId, customerIds);

        List<InvoiceListItem> rows = new ArrayList<>();
        for (AmcInvoice inv : all) {
            AmcSubscription s = subById.get(inv.getAmcSubscriptionId());
            if (s == null) {
                continue;
            }
            Car car = s.getCarId() == null ? null : carById.get(s.getCarId());
            Bike bike = s.getBikeId() == null ? null : bikeById.get(s.getBikeId());
            UUID customerId = car != null ? car.getCustomerId() : bike != null ? bike.getCustomerId() : null;
            Customer c = customerId == null ? null : customerById.get(customerId);
            rows.add(new InvoiceListItem(inv.getId(), "AMC", inv.getInvoiceNumber(), inv.getInvoiceDate(),
                    null, inv.getAmcSubscriptionId(), c == null ? null : c.getName(), c == null ? null : c.getPhone(),
                    car != null ? car.getCarNumber() : bike != null ? bike.getBikeNumber() : null,
                    s.getSaleGross(), inv.getCreatedAt()));
        }
        return rows;
    }

    private Map<UUID, Customer> customersOf(UUID orgId, Collection<UUID> customerIds) {
        List<UUID> distinct = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : byId(customers.findByOrgIdAndIdIn(orgId, distinct), Customer::getId);
    }

    private static <T> List<UUID> ids(Collection<T> source, Function<T, UUID> idOf) {
        List<UUID> out = source.stream().map(idOf).filter(Objects::nonNull).distinct().toList();
        // An empty IN () is invalid on some dialects; a random id matches nothing.
        return out.isEmpty() ? List.of(new UUID(0, 0)) : out;
    }

    private static <T> Map<UUID, T> byId(Collection<T> source, Function<T, UUID> idOf) {
        return source.stream().collect(Collectors.toMap(idOf, Function.identity(), (a, b) -> a));
    }

    private static boolean matches(InvoiceListItem r, String needle, String needleDigits) {
        String label = ("AMC".equals(r.type()) ? "amc-" : "inv-") + String.format("%04d", r.invoiceNumber());
        return label.contains(needle)
                || String.valueOf(r.invoiceNumber()).equals(needle)
                || (r.customerName() != null && r.customerName().toLowerCase().contains(needle))
                || (r.vehicleNumber() != null && r.vehicleNumber().toLowerCase().replace(" ", "").contains(needle.replace(" ", "")))
                || (!needleDigits.isEmpty() && r.customerPhone() != null
                && r.customerPhone().replaceAll("\\D", "").contains(needleDigits));
    }

    private static Comparator<InvoiceListItem> comparator(String sort, boolean asc) {
        Comparator<InvoiceListItem> byDate = Comparator.comparing(InvoiceListItem::invoiceDate)
                .thenComparing(InvoiceListItem::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        Comparator<InvoiceListItem> c = switch (sort == null ? "date" : sort.toLowerCase()) {
            case "amount" -> Comparator.comparing(
                    (InvoiceListItem r) -> r.grossTotal() == null ? BigDecimal.ZERO : r.grossTotal()).thenComparing(byDate);
            case "number" -> Comparator.comparingInt(InvoiceListItem::invoiceNumber).thenComparing(InvoiceListItem::type);
            default -> byDate;
        };
        return asc ? c : c.reversed();
    }
}
