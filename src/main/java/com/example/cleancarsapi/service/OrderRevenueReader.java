package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ChartBreakdownRow;
import com.example.cleancarsapi.dto.OrderRevenue;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads orders together with their lines and payments as {@link OrderRevenue} — the one
 * source of paid / unpaid money for {@code ChartService} and {@code DashboardService}, so
 * both count split payments the same way.
 */
@Component
@RequiredArgsConstructor
public class OrderRevenueReader {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final PaymentRepository payments;

    /** Every order created in {@code [from, toExclusive)}, any status — callers pick the paid / unpaid side they need. */
    public List<OrderRevenue> createdBetween(UUID orgId, LocalDateTime from, LocalDateTime toExclusive) {
        return build(orders.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(orgId, from, toExclusive));
    }

    /**
     * Single-use net price of every non-cancelled AMC redemption created in {@code [from, toExclusive)}: its
     * lines at full price (before the 100% redemption discount, before tax) — what the visit is worth, even
     * though the customer already paid upfront at the AMC sale. One entry per redemption.
     */
    public List<BigDecimal> amcRedemptionUseNet(UUID orgId, LocalDateTime from, LocalDateTime toExclusive) {
        List<ServiceOrder> redemptions = orders.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(orgId, from, toExclusive)
                .stream()
                .filter(o -> o.isAmcRedemption() && o.getStatus() != ServiceOrderStatus.CANCELLED)
                .toList();
        if (redemptions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ServiceOrderItem>> lines = items.findByServiceOrderIdInOrderByCreatedAtAscIdAsc(
                        redemptions.stream().map(ServiceOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));
        return redemptions.stream()
                .map(o -> lines.getOrDefault(o.getId(), List.of()).stream()
                        .map(i -> TaxBreakdown.of(i.getBasePrice(), i.getTaxPercentage(), i.isTaxIncluded()).times(i.getQuantity()).net())
                        .reduce(TaxBreakdown.zero().net(), BigDecimal::add))
                .toList();
    }

    /**
     * Service lines of every non-cancelled, non-AMC-redemption order created in {@code [from, toExclusive)},
     * summed per service name (case-insensitive, first spelling kept): {@code count} = units performed
     * (line quantity), {@code revenue} = net-of-tax money received for those lines (each line's net share of
     * its order's paid net, so part-paid orders count only what was paid).
     */
    public List<ChartBreakdownRow> serviceBreakdown(UUID orgId, LocalDateTime from, LocalDateTime toExclusive) {
        List<ServiceOrder> rows = orders.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(orgId, from, toExclusive)
                .stream()
                .filter(o -> !o.isAmcRedemption() && o.getStatus() != ServiceOrderStatus.CANCELLED)
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ServiceOrderItem>> lines = items.findByServiceOrderIdInOrderByCreatedAtAscIdAsc(
                        rows.stream().map(ServiceOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));
        Map<String, ChartBreakdownRow> byName = new LinkedHashMap<>();
        for (ServiceOrder order : rows) {
            List<ServiceOrderItem> orderLines = lines.getOrDefault(order.getId(), List.of());
            BigDecimal gross = orderLines.stream().map(i -> TaxBreakdown.ofLine(i).gross())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paidShare = gross.signum() == 0 || order.getAmountPaid().signum() == 0
                    ? BigDecimal.ZERO
                    : order.getAmountPaid().min(gross).divide(gross, 8, RoundingMode.HALF_UP);
            for (ServiceOrderItem line : orderLines) {
                BigDecimal paidNet = TaxBreakdown.ofLine(line).net().multiply(paidShare).setScale(2, RoundingMode.HALF_UP);
                String name = line.getServiceName().trim();
                byName.merge(name.toLowerCase(), new ChartBreakdownRow(name, line.getQuantity(), paidNet),
                        (a, b) -> new ChartBreakdownRow(a.name(), a.count() + b.count(), a.revenue().add(b.revenue())));
            }
        }
        return List.copyOf(byName.values());
    }

    /**
     * Lifetime money received per customer: {@code [customerId, Σ paidNet, non-cancelled order count]} — same
     * paid-net definition as the revenue tile, over every order the org ever created.
     */
    public Map<UUID, Object[]> lifetimeByCustomer(UUID orgId) {
        List<ServiceOrder> rows = orders.findByOrgId(orgId);
        List<OrderRevenue> revenue = build(rows); // same order as `rows`
        Map<UUID, Object[]> byCustomer = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ServiceOrder o = rows.get(i);
            if (o.getCustomerId() == null) {
                continue;
            }
            Object[] agg = byCustomer.computeIfAbsent(o.getCustomerId(), k -> new Object[]{BigDecimal.ZERO, 0L});
            agg[0] = ((BigDecimal) agg[0]).add(revenue.get(i).paidNet());
            if (o.getStatus() != ServiceOrderStatus.CANCELLED) {
                agg[1] = (Long) agg[1] + 1;
            }
        }
        return byCustomer;
    }

    /** The unpaid book: every non-cancelled order not yet fully paid (nothing received, or part-paid), all-time. */
    public List<OrderRevenue> stillOwing(UUID orgId) {
        return build(orders.findByOrgIdAndPaidFalseAndStatusNot(orgId, ServiceOrderStatus.CANCELLED));
    }

    private List<OrderRevenue> build(List<ServiceOrder> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(ServiceOrder::getId).toList();
        Map<UUID, List<ServiceOrderItem>> lines = items.findByServiceOrderIdInOrderByCreatedAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(ServiceOrderItem::getServiceOrderId));
        Map<UUID, List<Payment>> receipts = payments.findByServiceOrderIdIn(ids).stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate).thenComparing(Payment::getCreatedAt))
                .collect(Collectors.groupingBy(Payment::getServiceOrderId));

        return rows.stream().map(o -> {
            TaxBreakdown total = lines.getOrDefault(o.getId(), List.of()).stream()
                    .map(i -> TaxBreakdown.ofLine(i))
                    .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
            return new OrderRevenue(o.getCreatedAt(), o.getEmployeeId(), o.getStatus(), total.net(), total.gross(),
                    o.getAmountPaid(),
                    receipts.getOrDefault(o.getId(), List.of()).stream()
                            .map(p -> new OrderRevenue.Receipt(p.getPaymentType(), p.getAmount())).toList());
        }).toList();
    }
}
