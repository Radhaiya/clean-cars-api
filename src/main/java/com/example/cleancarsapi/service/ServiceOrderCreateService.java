package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the service-order CRUD — opens the job and snapshots its lines. */
@Service
@RequiredArgsConstructor
public class ServiceOrderCreateService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final ServiceOrderReferenceValidator references;
    private final ServiceOrderItemFactory itemFactory;
    private final ServiceOrderAssembler assembler;

    @Transactional
    public ServiceOrderResponse create(UUID orgId, ServiceOrderRequest request) {
        UUID customerId = references.resolveVehicle(orgId, request);

        ServiceOrder order = new ServiceOrder();
        order.setOrgId(orgId);
        order.setCarId(request.carId());
        order.setBikeId(request.bikeId());
        order.setCustomerId(customerId);
        order.setCreatedBy(AuthContext.require().userId());
        request.applyTo(order);
        order.transitionTo(request.status() == null ? ServiceOrderStatus.IN_PROGRESS : request.status());

        ServiceOrder saved = orders.save(order);
        items.saveAll(itemFactory.build(orgId, saved.getId(), request.safeItems()));

        return assembler.toResponse(orgId, saved);
    }
}
