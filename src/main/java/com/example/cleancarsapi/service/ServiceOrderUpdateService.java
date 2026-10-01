package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PaymentPlanRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * UPDATE half of the service-order CRUD. The order's car (and therefore customer)
 * is fixed; {@code items} replaces the whole line set.
 */
@Service
@RequiredArgsConstructor
public class ServiceOrderUpdateService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final ServiceOrderReferenceValidator references;
    private final ServiceOrderItemFactory itemFactory;
    private final ServiceOrderAssembler assembler;
    private final ServiceOrderPaymentLedger ledger;

    @Transactional
    public ServiceOrderResponse update(UUID orgId, UUID id, ServiceOrderRequest request) {
        ServiceOrder order = orders.lockByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));

        references.validateAssignments(orgId, request);
        if (order.isAmcRedemption()) {
            return updateAmcRedemption(orgId, order, request);
        }
        request.applyTo(order);
        if (request.payments() != null) {
            // the payments are replaced below under the new plan, so the "no payments yet" guard doesn't apply
            if (request.paymentPlan() != null) {
                order.setPaymentPlan(request.paymentPlan());
            }
        } else {
            ledger.changePlan(order, request.paymentPlan());
        }
        if (request.status() != null) {
            order.transitionTo(request.status());
        }
        orders.save(order);

        items.deleteByServiceOrderId(id);
        items.flush();
        items.saveAll(itemFactory.build(orgId, id, request.safeItems()));
        if (request.payments() != null) {
            ledger.replacePayments(order, request.payments());
        } else {
            // the lines (and so the total) may have changed — re-derive paid against the money already received
            ledger.refresh(order);
        }

        return assembler.toResponse(orgId, order);
    }

    /**
     * An AMC redemption keeps its lines (100% discount) and has no payments: only the assignee,
     * odometer, vendor, notes and status can change. Sending lines or payments is refused so the
     * order can never turn into a bill.
     */
    private ServiceOrderResponse updateAmcRedemption(UUID orgId, ServiceOrder order, ServiceOrderRequest request) {
        if (!request.safeItems().isEmpty() || (request.payments() != null && !request.payments().isEmpty())) {
            throw ConflictException.amcOrderLocked();
        }
        request.applyTo(order);
        if (request.status() != null) {
            transition(order, request.status());
        }
        orders.save(order);
        return assembler.toResponse(orgId, order);
    }

    /** Move to a new status; a cancelled AMC order can never be reopened (its slot was freed). */
    private void transition(ServiceOrder order, ServiceOrderStatus status) {
        if (order.isAmcRedemption() && order.getStatus() == ServiceOrderStatus.CANCELLED
                && status != ServiceOrderStatus.CANCELLED) {
            throw ConflictException.amcOrderReopen();
        }
        order.transitionTo(status);
    }

    /** Quick edit — switch between one-time and split. Back to one-time only while no payments exist. */
    @Transactional
    public ServiceOrderResponse setPaymentPlan(UUID orgId, UUID id, PaymentPlanRequest request) {
        ServiceOrder order = orders.lockByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        ledger.changePlan(order, request.paymentPlan());
        orders.save(order);
        return assembler.toResponse(orgId, order);
    }

    /** Quick edit — move the order to a new status (stamps/clears completedAt to match). */
    @Transactional
    public ServiceOrderResponse setStatus(UUID orgId, UUID id, ServiceOrderStatus status) {
        ServiceOrder order = load(orgId, id);
        transition(order, status);
        orders.save(order);
        return assembler.toResponse(orgId, order);
    }

    private ServiceOrder load(UUID orgId, UUID id) {
        return orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
    }
}
