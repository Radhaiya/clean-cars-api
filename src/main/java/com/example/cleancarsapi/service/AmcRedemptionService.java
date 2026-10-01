package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.AmcRedeemRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.AmcSubscriptionItem;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcSubscriptionItemRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Redeems one AMC period (docs/FEATURE-AMC.md): creates a service order for the AMC's vehicle whose
 * lines are the AMC's bundle at a hard-set 100% discount (the server builds them — the client can't send
 * lines, prices or discounts), so the order totals ₹0 and can never produce a bill. Strict slot usage:
 * only the current period, and only if no live redemption already holds it. The subscription row is
 * locked so concurrent redemptions (or a cancel + create race) can't put two orders in one slot.
 */
@Service
@RequiredArgsConstructor
public class AmcRedemptionService {

    private final AmcSubscriptionRepository subscriptions;
    private final AmcSubscriptionItemRepository subscriptionItems;
    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository orderItems;
    private final ServiceOrderReferenceValidator references;
    private final ServiceOrderPaymentLedger ledger;
    private final ServiceOrderAssembler assembler;
    private final PlanLimitService planLimits;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional
    public ServiceOrderResponse redeem(UUID orgId, UUID subscriptionId, AmcRedeemRequest request) {
        planLimits.assertAmcEnabled(orgId);
        AmcSubscription sub = subscriptions.lockByIdAndOrgId(subscriptionId, orgId)
                .orElseThrow(() -> new NotFoundException("amc", subscriptionId));

        LocalDate today = orgTimezones.now().toLocalDate();
        Set<Integer> used = AmcSubscriptionAssembler.usedSlotsOf(orders, List.of(sub.getId()))
                .getOrDefault(sub.getId(), Set.of());
        AmcSlots.Counts counts = AmcSlots.compute(sub.getStartDate(), sub.getTenureMonths(), sub.getIntervalMonths(),
                today, used);
        switch (counts.status()) {
            case UPCOMING -> throw ConflictException.amcNotStarted();
            case EXPIRED -> throw ConflictException.amcExpired();
            default -> { }
        }
        if (used.contains(counts.currentSlot())) {
            throw ConflictException.amcSlotUsed();
        }

        // Resolves the AMC's vehicle (live owner required, like any new order) and validates employee / vendor.
        UUID customerId = references.resolveVehicle(orgId, new ServiceOrderRequest(sub.getCarId(), sub.getBikeId(),
                request.employeeId(), request.odometerReading(), request.vendorId(), null, null, null,
                request.notes(), null));

        ServiceOrder order = new ServiceOrder();
        order.setOrgId(orgId);
        order.setCarId(sub.getCarId());
        order.setBikeId(sub.getBikeId());
        order.setCustomerId(customerId);
        order.setCreatedBy(AuthContext.require().userId());
        order.setEmployeeId(request.employeeId());
        order.setOdometerReading(request.odometerReading());
        order.setVendorId(request.vendorId());
        order.setNotes(request.notes());
        order.setAmcSubscriptionId(sub.getId());
        order.setAmcSlotIndex(counts.currentSlot());
        order.transitionTo(ServiceOrderStatus.IN_PROGRESS);
        ServiceOrder saved = orders.save(order);

        for (AmcSubscriptionItem item : subscriptionItems.findBySubscriptionIdInOrderByPositionAsc(List.of(sub.getId()))) {
            ServiceOrderItem line = new ServiceOrderItem();
            line.setServiceOrderId(saved.getId());
            line.setServiceName(item.getServiceName());
            line.setBasePrice(item.getPrice());
            line.setTaxPercentage(item.getTaxPercentage());
            line.setTaxIncluded(item.isTaxIncluded());
            line.setQuantity(item.getQuantity());
            line.setDiscountAmount(item.getPrice()); // hard-set 100%: never editable, never a bill
            orderItems.save(line);
        }
        ledger.refresh(saved); // zero total → settled (paid) automatically
        return assembler.toResponse(orgId, saved);
    }
}
