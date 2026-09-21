package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
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

    @Transactional
    public ServiceOrderResponse update(UUID orgId, UUID id, ServiceOrderRequest request) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));

        references.validateAssignments(orgId, request);
        request.applyTo(order);
        if (request.status() != null) {
            order.transitionTo(request.status());
        }
        orders.save(order);

        items.deleteByServiceOrderId(id);
        items.flush();
        items.saveAll(itemFactory.build(orgId, id, request.safeItems()));

        return assembler.toResponse(orgId, order);
    }

    /** Quick edit — flip paid/unpaid without touching anything else (incl. paymentDate). */
    @Transactional
    public ServiceOrderResponse setPaid(UUID orgId, UUID id, boolean paid) {
        ServiceOrder order = load(orgId, id);
        order.setPaid(paid);
        orders.save(order);
        return assembler.toResponse(orgId, order);
    }

    /** Quick edit — move the order to a new status (stamps/clears completedAt to match). */
    @Transactional
    public ServiceOrderResponse setStatus(UUID orgId, UUID id, ServiceOrderStatus status) {
        ServiceOrder order = load(orgId, id);
        order.transitionTo(status);
        orders.save(order);
        return assembler.toResponse(orgId, order);
    }

    private ServiceOrder load(UUID orgId, UUID id) {
        return orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
    }
}
