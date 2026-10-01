package com.example.cleancarsapi.service;

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

import java.time.LocalDateTime;
import java.util.Comparator;
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
