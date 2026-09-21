package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the service-order CRUD — removes the order and its lines. */
@Service
@RequiredArgsConstructor
public class ServiceOrderDeleteService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        items.deleteByServiceOrderId(id);
        orders.delete(order);
    }
}
