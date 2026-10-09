package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.AmcSubscriptionItem;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.AmcInvoiceRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionItemRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds {@link AmcSubscriptionResponse}s (batched): snapshot rows, seller names and the runtime slot counts. */
@Component
@RequiredArgsConstructor
public class AmcSubscriptionAssembler {

    private final AmcSubscriptionItemRepository itemRepo;
    private final AmcInvoiceRepository invoices;
    private final EmployeeRepository employees;
    private final OrgTimeZoneResolver orgTimezones;
    private final ServiceOrderRepository orders;

    public AmcSubscriptionResponse toResponse(UUID orgId, AmcSubscription subscription) {
        return toResponses(orgId, List.of(subscription)).get(0);
    }

    public List<AmcSubscriptionResponse> toResponses(UUID orgId, List<AmcSubscription> subs) {
        if (subs.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = subs.stream().map(AmcSubscription::getId).toList();
        Map<UUID, List<AmcSubscriptionItem>> items = itemRepo.findBySubscriptionIdInOrderByPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(AmcSubscriptionItem::getSubscriptionId));
        Set<UUID> sellerIds = subs.stream().map(AmcSubscription::getSoldByEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> sellerNames = sellerIds.isEmpty() ? Map.of()
                : employees.findByOrgIdAndIdIn(orgId, sellerIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Set<UUID> invoiced = Set.copyOf(invoices.findSubscriptionIdsWithInvoice(ids));
        LocalDate today = orgTimezones.now().toLocalDate();
        Map<UUID, Set<Integer>> usedSlots = usedSlots(ids);

        return subs.stream().map(s -> {
            AmcSlots.Counts counts = AmcSlots.compute(s.getStartDate(), s.getTenureMonths(), s.getIntervalMonths(),
                    today, usedSlots.getOrDefault(s.getId(), Set.of()));
            return AmcSubscriptionResponse.from(s, items.getOrDefault(s.getId(), List.of()), counts,
                    s.getSoldByEmployeeId() == null ? null : sellerNames.get(s.getSoldByEmployeeId()), invoiced.contains(s.getId()));
        }).toList();
    }

    /** Slots with a live (non-cancelled) redemption order, per subscription. */
    private Map<UUID, Set<Integer>> usedSlots(List<UUID> subscriptionIds) {
        return usedSlotsOf(orders, subscriptionIds);
    }

    static Map<UUID, Set<Integer>> usedSlotsOf(ServiceOrderRepository orders, java.util.Collection<UUID> subscriptionIds) {
        return orders.liveAmcSlots(subscriptionIds, ServiceOrderStatus.CANCELLED).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0],
                        Collectors.mapping(row -> (Integer) row[1], Collectors.toSet())));
    }
}
