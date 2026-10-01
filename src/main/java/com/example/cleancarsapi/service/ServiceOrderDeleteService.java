package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the service-order CRUD — removes the order, its lines and its payments. */
@Service
@RequiredArgsConstructor
public class ServiceOrderDeleteService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final PaymentRepository payments;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        ServiceOrder order = orders.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service order", id));
        payments.deleteByServiceOrderId(id);
        items.deleteByServiceOrderId(id);
        orders.delete(order);
    }
}
